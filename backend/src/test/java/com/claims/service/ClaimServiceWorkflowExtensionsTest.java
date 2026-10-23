package com.claims.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claims.dto.ClaimHistoryResponse;
import com.claims.dto.ClaimResponse;
import com.claims.dto.CreateClaimRequest;
import com.claims.dto.PageResponse;
import com.claims.entity.Claim;
import com.claims.entity.ClaimStatus;
import com.claims.entity.ClaimType;
import com.claims.entity.Role;
import com.claims.entity.User;
import com.claims.exception.BusinessException;
import com.claims.repository.ClaimRepository;
import com.claims.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** Covers the claimant information flow, officer queue/pick-up, status filter, history and audit recording. */
@ExtendWith(MockitoExtension.class)
class ClaimServiceWorkflowExtensionsTest {
    @Mock ClaimRepository claims;
    @Mock UserRepository users;
    @Mock ClaimAuditRecorder audit;
    @InjectMocks ClaimService service;

    private User claimant;
    private User stranger;
    private User officer;
    private User otherOfficer;
    private User manager;

    @BeforeEach
    void setUp() {
        claimant = user("claimant@example.com", Role.CLAIMANT, 1L);
        stranger = user("stranger@example.com", Role.CLAIMANT, 2L);
        officer = user("officer@claims.local", Role.OFFICER, 3L);
        otherOfficer = user("officer2@claims.local", Role.OFFICER, 4L);
        manager = user("manager@claims.local", Role.MANAGER, 5L);
    }

    // ---- claimant provides additional information ----

    @Test
    void claimantAnswersInformationRequestAndClaimReturnsToReview() {
        Claim claim = claim(ClaimStatus.MORE_INFO_REQUIRED, officer);
        claim.setInformationRequest("Send the repair estimate");
        givenActor(claimant);
        givenClaim(10L, claim);
        saveReturnsArgument();

        ClaimResponse result = service.provideInformation(10L, "  Estimate attached: 900 SGD  ", "claimant@example.com");

        assertEquals(ClaimStatus.UNDER_REVIEW, result.status());
        assertEquals("Estimate attached: 900 SGD", result.informationResponse());
        assertEquals("Send the repair estimate", result.informationRequest());
        verify(audit).record(claim, ClaimStatus.MORE_INFO_REQUIRED, ClaimStatus.UNDER_REVIEW, claimant,
                "Estimate attached: 900 SGD");
    }

    @Test
    void informationCanOnlyBeProvidedWhenRequested() {
        Claim claim = claim(ClaimStatus.SUBMITTED, null);
        givenActor(claimant);
        givenClaim(10L, claim);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.provideInformation(10L, "anything", "claimant@example.com"));

        assertEquals(409, error.getStatus().value());
        assertEquals(ClaimStatus.SUBMITTED, claim.getStatus());
        verify(claims, never()).save(any());
    }

    @Test
    void claimantCannotAnswerSomeoneElsesClaim() {
        givenActor(stranger);
        givenClaim(10L, claim(ClaimStatus.MORE_INFO_REQUIRED, officer));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.provideInformation(10L, "hello", "stranger@example.com"));

        assertEquals(404, error.getStatus().value());
        verify(claims, never()).save(any());
    }

    @Test
    void staffCannotAnswerOnBehalfOfTheClaimant() {
        givenActor(officer);
        givenClaim(10L, claim(ClaimStatus.MORE_INFO_REQUIRED, officer));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.provideInformation(10L, "hello", "officer@claims.local"));

        assertEquals(404, error.getStatus().value());
    }

    @Test
    void requestingInformationAgainClearsThePreviousAnswer() {
        Claim claim = claim(ClaimStatus.UNDER_REVIEW, officer);
        claim.setInformationResponse("old answer");
        givenActor(officer);
        givenClaim(10L, claim);
        saveReturnsArgument();

        ClaimResponse result = service.requestInformation(10L, "Need the police report", "officer@claims.local");

        assertNull(result.informationResponse());
        assertEquals("Need the police report", result.informationRequest());
        verify(audit).record(claim, ClaimStatus.UNDER_REVIEW, ClaimStatus.MORE_INFO_REQUIRED, officer,
                "Need the police report");
    }

    // ---- officer queue and pick-up ----

    @Test
    void officerSeesUnassignedSubmittedClaimsInTheQueue() {
        Claim waiting = claim(ClaimStatus.SUBMITTED, null);
        givenActor(officer);
        when(claims.findByAssignedOfficerIsNullAndStatus(eq(ClaimStatus.SUBMITTED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(waiting)));

        PageResponse<ClaimResponse> queue = service.queue("officer@claims.local", 0, 20);

        assertEquals(1, queue.content().size());
        assertNull(queue.content().get(0).assignedOfficerId());
    }

    @Test
    void claimantsCannotReadTheStaffQueue() {
        givenActor(claimant);

        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.queue("claimant@example.com", 0, 20)).getStatus().value());
    }

    @Test
    void officerPicksUpAnUnassignedClaim() {
        Claim claim = claim(ClaimStatus.SUBMITTED, null);
        givenActor(officer);
        givenClaim(10L, claim);
        when(users.findById(3L)).thenReturn(Optional.of(officer));
        saveReturnsArgument();

        ClaimResponse result = service.pickUp(10L, "officer@claims.local");

        assertEquals(3L, result.assignedOfficerId());
    }

    @Test
    void officerCannotPickUpAClaimOwnedByAnotherOfficer() {
        givenActor(officer);
        givenClaim(10L, claim(ClaimStatus.UNDER_REVIEW, otherOfficer));
        when(users.findById(3L)).thenReturn(Optional.of(officer));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.pickUp(10L, "officer@claims.local"));

        assertEquals(409, error.getStatus().value());
        verify(claims, never()).save(any());
    }

    @Test
    void onlyOfficersCanPickUp() {
        givenActor(manager);

        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.pickUp(10L, "manager@claims.local")).getStatus().value());
    }

    // ---- visibility ----

    @Test
    void officerCanViewQueueClaimsAndOwnClaimsButNotOthers() {
        givenActor(officer);
        when(claims.findById(1L)).thenReturn(Optional.of(claim(ClaimStatus.SUBMITTED, null)));
        when(claims.findById(2L)).thenReturn(Optional.of(claim(ClaimStatus.UNDER_REVIEW, officer)));
        when(claims.findById(3L)).thenReturn(Optional.of(claim(ClaimStatus.UNDER_REVIEW, otherOfficer)));

        assertNotNull(service.get(1L, "officer@claims.local"));
        assertNotNull(service.get(2L, "officer@claims.local"));
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.get(3L, "officer@claims.local")).getStatus().value());
    }

    // ---- status filter on the list ----

    @Test
    void claimantListFiltersByStatus() {
        givenActor(claimant);
        when(claims.findByClaimantIdAndStatus(eq(1L), eq(ClaimStatus.APPROVED), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertTrue(service.list("claimant@example.com", false, 0, 20, ClaimStatus.APPROVED).content().isEmpty());
        verify(claims).findByClaimantIdAndStatus(eq(1L), eq(ClaimStatus.APPROVED), any(Pageable.class));
    }

    @Test
    void officerListFiltersByStatus() {
        givenActor(officer);
        when(claims.findByAssignedOfficerIdAndStatus(eq(3L), eq(ClaimStatus.UNDER_REVIEW), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list("officer@claims.local", false, 0, 20, ClaimStatus.UNDER_REVIEW);

        verify(claims).findByAssignedOfficerIdAndStatus(eq(3L), eq(ClaimStatus.UNDER_REVIEW), any(Pageable.class));
    }

    @Test
    void managerListFiltersByStatusOrReturnsEverything() {
        givenActor(manager);
        when(claims.findByStatus(eq(ClaimStatus.SETTLED), any(Pageable.class))).thenReturn(Page.empty());
        when(claims.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.list("manager@claims.local", true, 0, 20, ClaimStatus.SETTLED);
        service.list("manager@claims.local", true, 0, 20, null);

        verify(claims).findByStatus(eq(ClaimStatus.SETTLED), any(Pageable.class));
        verify(claims).findAll(any(Pageable.class));
    }

    @Test
    void officerAndClaimantListsWithoutStatusUseTheUnfilteredQueries() {
        givenActor(officer);
        when(claims.findByAssignedOfficerId(eq(3L), any(Pageable.class))).thenReturn(Page.empty());

        service.list("officer@claims.local", false, 0, 20);

        verify(claims).findByAssignedOfficerId(eq(3L), any(Pageable.class));
    }

    // ---- history and audit ----

    @Test
    void historyIsReturnedForThoseWhoMayViewTheClaim() {
        Claim claim = claim(ClaimStatus.UNDER_REVIEW, officer);
        ReflectionTestUtils.setField(claim, "id", 10L);
        List<ClaimHistoryResponse> entries = List.of(new ClaimHistoryResponse(1L, null, ClaimStatus.SUBMITTED, 1L,
                "Jane", "Claim submitted", Instant.now()));
        givenActor(claimant);
        givenClaim(10L, claim);
        when(audit.history(10L)).thenReturn(entries);

        assertEquals(entries, service.history(10L, "claimant@example.com"));
    }

    @Test
    void historyIsHiddenFromOtherClaimants() {
        givenActor(stranger);
        givenClaim(10L, claim(ClaimStatus.SUBMITTED, null));

        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.history(10L, "stranger@example.com")).getStatus().value());
        verify(audit, never()).history(any());
    }

    @Test
    void submittingAClaimRecordsTheInitialHistoryAndNormalisesTheMarket() {
        givenActor(claimant);
        saveReturnsArgument();
        CreateClaimRequest request = new CreateClaimRequest("POL-1", "Jane", "claimant@example.com",
                LocalDate.now(), "Water damage from a burst pipe in the kitchen.", new BigDecimal("500.00"),
                ClaimType.PROPERTY, " sg ");

        ClaimResponse result = service.create(request, "claimant@example.com");

        assertEquals("SG", result.market());
        assertEquals(ClaimType.PROPERTY, result.claimType());
        verify(audit).record(any(Claim.class), isNull(), eq(ClaimStatus.SUBMITTED), eq(claimant),
                eq("Claim submitted"));
    }

    @Test
    void everyOfficerDecisionIsRecordedWithItsNote() {
        Claim claim = claim(ClaimStatus.UNDER_REVIEW, officer);
        givenActor(officer);
        givenClaim(10L, claim);
        saveReturnsArgument();

        service.decide(10L, ClaimStatus.APPROVED, "  Within cover  ", "officer@claims.local");

        verify(audit).record(claim, ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, officer, "Within cover");
    }

    @Test
    void reviewRecordsHistoryWithoutANote() {
        Claim claim = claim(ClaimStatus.SUBMITTED, officer);
        givenActor(officer);
        givenClaim(10L, claim);
        saveReturnsArgument();

        service.review(10L, "officer@claims.local");

        verify(audit).record(claim, ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, officer, null);
    }

    // ---- helpers ----

    private void givenActor(User actor) {
        when(users.findByEmailIgnoreCase(actor.getEmail())).thenReturn(Optional.of(actor));
    }

    private void givenClaim(Long id, Claim claim) {
        when(claims.findById(id)).thenReturn(Optional.of(claim));
    }

    private void saveReturnsArgument() {
        when(claims.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Claim claim(ClaimStatus status, User assignedOfficer) {
        Claim claim = new Claim("CLM-TEST", "POL-123", "Jane Claimant", "claimant@example.com", LocalDate.now(),
                "Rear bumper damage from a parking collision.", new BigDecimal("100.00"), ClaimType.MOTOR, "SG", claimant);
        claim.transitionTo(status);
        if (assignedOfficer != null) claim.assign(assignedOfficer);
        return claim;
    }

    private static User user(String email, Role role, Long id) {
        User user = new User(email, "encoded", "Name " + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
