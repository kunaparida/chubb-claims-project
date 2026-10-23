package com.claims.controller;

import com.claims.dto.*;
import com.claims.entity.ClaimStatus;
import com.claims.service.ClaimService;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/claims")
@SecurityRequirement(name = "bearerAuth")
public class ClaimController {
    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) { this.claimService = claimService; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLAIMANT')")
    public ClaimResponse create(@Valid @RequestBody CreateClaimRequest request, @AuthenticationPrincipal UserDetails user) {
        return claimService.create(request, user.getUsername());
    }

    /** Claimants see their own claims, officers their assigned claims, managers all. Optional status filter. */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLAIMANT','OFFICER','MANAGER')")
    public PageResponse<ClaimResponse> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) ClaimStatus status,
                                            @AuthenticationPrincipal UserDetails user) {
        return claimService.list(user.getUsername(), false, page, size, status);
    }

    /** Officer work queue: submitted claims that nobody has picked up yet, oldest first. */
    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    public PageResponse<ClaimResponse> queue(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @AuthenticationPrincipal UserDetails user) {
        return claimService.queue(user.getUsername(), page, size);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('CLAIMANT','OFFICER','MANAGER')")
    public List<ClaimHistoryResponse> history(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return claimService.history(id, user.getUsername());
    }

    /** The claimant answers an information request; the claim returns to UNDER_REVIEW. */
    @PutMapping("/{id}/provide-info")
    @PreAuthorize("hasRole('CLAIMANT')")
    public ClaimResponse provideInformation(@PathVariable Long id, @Valid @RequestBody ProvideInformationRequest request,
                                            @AuthenticationPrincipal UserDetails user) {
        return claimService.provideInformation(id, request.response(), user.getUsername());
    }

    /** An officer takes an unassigned claim from the queue. */
    @PutMapping("/{id}/pickup")
    @PreAuthorize("hasRole('OFFICER')")
    public ClaimResponse pickUp(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return claimService.pickUp(id, user.getUsername());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLAIMANT','OFFICER','MANAGER')")
    public ClaimResponse get(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return claimService.get(id, user.getUsername());
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    public ClaimResponse assign(@PathVariable Long id, @Valid @RequestBody AssignClaimRequest request,
                                @AuthenticationPrincipal UserDetails user) {
        return claimService.assign(id, request.officerId(), user.getUsername());
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    public ClaimResponse review(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return claimService.review(id, user.getUsername());
    }

    @PutMapping("/{id}/request-info")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    public ClaimResponse requestInformation(@PathVariable Long id, @Valid @RequestBody NoteRequest request,
                                            @AuthenticationPrincipal UserDetails user) {
        return claimService.requestInformation(id, request.note(), user.getUsername());
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    public ClaimResponse approve(@PathVariable Long id, @Valid @RequestBody NoteRequest request,
                                 @AuthenticationPrincipal UserDetails user) {
        return claimService.decide(id, com.claims.entity.ClaimStatus.APPROVED, request.note(), user.getUsername());
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    public ClaimResponse reject(@PathVariable Long id, @Valid @RequestBody NoteRequest request,
                                @AuthenticationPrincipal UserDetails user) {
        return claimService.decide(id, com.claims.entity.ClaimStatus.REJECTED, request.note(), user.getUsername());
    }

    @PutMapping("/{id}/settle")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    public ClaimResponse settle(@PathVariable Long id, @Valid @RequestBody NoteRequest request,
                                @AuthenticationPrincipal UserDetails user) {
        return claimService.decide(id, com.claims.entity.ClaimStatus.SETTLED, request.note(), user.getUsername());
    }
}
