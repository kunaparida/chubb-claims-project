package com.claims.service;

import com.claims.dto.*;
import com.claims.entity.*;
import com.claims.exception.BusinessException;
import com.claims.exception.ResourceNotFoundException;
import com.claims.mapper.ClaimMapper;
import com.claims.repository.ClaimRepository;
import com.claims.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.claims.util.ClaimNumberGenerator;

@Service
public class ClaimService {
    private final ClaimRepository claims;
    private final UserRepository users;
    private final ClaimAuditRecorder audit;

    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<ClaimStatus, Set<ClaimStatus>> TRANSITIONS = Map.of(
            ClaimStatus.SUBMITTED, Set.of(ClaimStatus.UNDER_REVIEW),
            ClaimStatus.UNDER_REVIEW, Set.of(ClaimStatus.MORE_INFO_REQUIRED, ClaimStatus.APPROVED, ClaimStatus.REJECTED),
            ClaimStatus.MORE_INFO_REQUIRED, Set.of(ClaimStatus.UNDER_REVIEW),
            ClaimStatus.APPROVED, Set.of(ClaimStatus.SETTLED),
            ClaimStatus.REJECTED, Set.of(),
            ClaimStatus.SETTLED, Set.of());

    public ClaimService(ClaimRepository claims, UserRepository users, ClaimAuditRecorder audit) {
        this.claims = claims;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public ClaimResponse create(CreateClaimRequest request, String actorEmail) {
        User claimant = userByEmail(actorEmail);
        if (!claimant.getEmail().equalsIgnoreCase(request.claimantEmail().trim())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Claimant email must match the authenticated account");
        }
        Claim claim = new Claim(ClaimNumberGenerator.generate(), request.policyNumber().trim(),
                request.claimantName().trim(), claimant.getEmail(), request.incidentDate(),
                request.description().trim(), request.amountClaimed(), request.claimType(),
                request.market().trim().toUpperCase(Locale.ROOT), claimant);
        Claim saved = claims.save(claim);
        audit.record(saved, null, ClaimStatus.SUBMITTED, claimant, "Claim submitted");
        return ClaimMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimResponse> list(String actorEmail, boolean manager, int page, int size) {
        return list(actorEmail, manager, page, size, null);
    }

    /**
     * Claimants see their own claims, officers the claims assigned to them, managers everything.
     * An optional status narrows the result.
     */
    @Transactional(readOnly = true)
    public PageResponse<ClaimResponse> list(String actorEmail, boolean manager, int page, int size,
                                            ClaimStatus status) {
        User actor = userByEmail(actorEmail);
        if (manager && actor.getRole() != Role.MANAGER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Manager access is required");
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Claim> result = switch (actor.getRole()) {
            case CLAIMANT -> status == null
                    ? claims.findByClaimantId(actor.getId(), pageable)
                    : claims.findByClaimantIdAndStatus(actor.getId(), status, pageable);
            case OFFICER -> status == null
                    ? claims.findByAssignedOfficerId(actor.getId(), pageable)
                    : claims.findByAssignedOfficerIdAndStatus(actor.getId(), status, pageable);
            case MANAGER -> status == null ? claims.findAll(pageable) : claims.findByStatus(status, pageable);
        };
        return PageResponse.of(result.map(ClaimMapper::toResponse));
    }

    /** Unassigned, newly submitted claims waiting for an officer to pick them up (oldest first). */
    @Transactional(readOnly = true)
    public PageResponse<ClaimResponse> queue(String actorEmail, int page, int size) {
        User actor = userByEmail(actorEmail);
        if (actor.getRole() == Role.CLAIMANT) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Officer or manager access is required");
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        return PageResponse.of(claims.findByAssignedOfficerIsNullAndStatus(ClaimStatus.SUBMITTED, pageable)
                .map(ClaimMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ClaimResponse get(Long id, String actorEmail) {
        User actor = userByEmail(actorEmail);
        Claim claim = findClaim(id);
        verifyCanView(claim, actor);
        return ClaimMapper.toResponse(claim);
    }

    /**
     * The claim, if the caller may view it (the same rules as {@link #get}); otherwise the 404 the API uses so that a claim's
     * existence is not revealed. For services that attach something to a claim, such as documents.
     */
    @Transactional(readOnly = true)
    public Claim viewableClaim(Long id, String actorEmail) {
        User actor = userByEmail(actorEmail);
        Claim claim = findClaim(id);
        verifyCanView(claim, actor);
        return claim;
    }

    @Transactional(readOnly = true)
    public List<ClaimHistoryResponse> history(Long id, String actorEmail) {
        User actor = userByEmail(actorEmail);
        Claim claim = findClaim(id);
        verifyCanView(claim, actor);
        return audit.history(claim.getId());
    }

    @Transactional
    public ClaimResponse assign(Long id, Long officerId, String actorEmail) {
        User actor = userByEmail(actorEmail);
        Claim claim = findClaim(id);
        User officer = users.findById(officerId)
                .filter(candidate -> candidate.getRole() == Role.OFFICER)
                .orElseThrow(() -> new ResourceNotFoundException("Officer not found"));
        if (actor.getRole() == Role.OFFICER) {
            if (!actor.getId().equals(officer.getId())) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Officers may only assign claims to themselves");
            }
            if (claim.getAssignedOfficer() != null && !claim.getAssignedOfficer().getId().equals(actor.getId())) {
                throw new BusinessException(HttpStatus.CONFLICT, "Claim is already assigned to another officer");
            }
        } else if (actor.getRole() != Role.MANAGER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Manager or officer access is required");
        }
        claim.assign(officer);
        return ClaimMapper.toResponse(claims.save(claim));
    }

    /** An officer takes an unassigned claim from the queue. */
    @Transactional
    public ClaimResponse pickUp(Long id, String actorEmail) {
        User actor = userByEmail(actorEmail);
        if (actor.getRole() != Role.OFFICER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only officers can pick up claims");
        }
        return assign(id, actor.getId(), actorEmail);
    }

    @Transactional
    public ClaimResponse review(Long id, String actorEmail) {
        Authorized auth = authorizedClaim(id, actorEmail);
        ClaimStatus from = auth.claim().getStatus();
        transition(auth.claim(), ClaimStatus.UNDER_REVIEW);
        auth.claim().setInformationRequest(null);
        return persist(auth.claim(), from, auth.actor(), null);
    }

    @Transactional
    public ClaimResponse requestInformation(Long id, String note, String actorEmail) {
        Authorized auth = authorizedClaim(id, actorEmail);
        ClaimStatus from = auth.claim().getStatus();
        transition(auth.claim(), ClaimStatus.MORE_INFO_REQUIRED);
        auth.claim().setInformationRequest(note.trim());
        auth.claim().setInformationResponse(null);
        return persist(auth.claim(), from, auth.actor(), note.trim());
    }

    /** The claimant answers an information request; the claim goes back to review. */
    @Transactional
    public ClaimResponse provideInformation(Long id, String response, String actorEmail) {
        User actor = userByEmail(actorEmail);
        Claim claim = findClaim(id);
        if (actor.getRole() != Role.CLAIMANT || !claim.getClaimant().getId().equals(actor.getId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Claim not found");
        }
        if (claim.getStatus() != ClaimStatus.MORE_INFO_REQUIRED) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "No additional information has been requested for this claim");
        }
        ClaimStatus from = claim.getStatus();
        transition(claim, ClaimStatus.UNDER_REVIEW);
        claim.setInformationResponse(response.trim());
        return persist(claim, from, actor, response.trim());
    }

    @Transactional
    public ClaimResponse decide(Long id, ClaimStatus next, String note, String actorEmail) {
        Authorized auth = authorizedClaim(id, actorEmail);
        ClaimStatus from = auth.claim().getStatus();
        transition(auth.claim(), next);
        String cleanNote = note == null ? null : note.trim();
        auth.claim().setDecisionNotes(cleanNote);
        return persist(auth.claim(), from, auth.actor(), cleanNote);
    }

    private ClaimResponse persist(Claim claim, ClaimStatus from, User actor, String note) {
        Claim saved = claims.save(claim);
        audit.record(saved, from, saved.getStatus(), actor, note);
        return ClaimMapper.toResponse(saved);
    }

    private record Authorized(User actor, Claim claim) {}

    private Authorized authorizedClaim(Long id, String email) {
        User actor = userByEmail(email);
        Claim claim = findClaim(id);
        if (actor.getRole() != Role.MANAGER) requireAssignedOfficer(claim, actor);
        return new Authorized(actor, claim);
    }

    /** Officers can see claims in the queue (unassigned) and claims assigned to themselves, nobody else's. */
    private void verifyCanView(Claim claim, User actor) {
        if (actor.getRole() == Role.CLAIMANT && !claim.getClaimant().getId().equals(actor.getId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Claim not found");
        }
        if (actor.getRole() == Role.OFFICER && claim.getAssignedOfficer() != null
                && !claim.getAssignedOfficer().getId().equals(actor.getId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Claim not found");
        }
    }

    private void requireAssignedOfficer(Claim claim, User actor) {
        if (actor.getRole() != Role.OFFICER || claim.getAssignedOfficer() == null
                || !claim.getAssignedOfficer().getId().equals(actor.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only the assigned officer or a manager can perform this action");
        }
    }

    private void transition(Claim claim, ClaimStatus next) {
        if (!TRANSITIONS.getOrDefault(claim.getStatus(), Set.of()).contains(next)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Invalid claim status transition: " + claim.getStatus() + " -> " + next);
        }
        claim.transitionTo(next);
    }

    private User userByEmail(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
    }

    private Claim findClaim(Long id) {
        return claims.findById(id).orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
    }
}
