package com.claims.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.claims.dto.ClaimResponse;
import com.claims.dto.CreateClaimRequest;
import com.claims.entity.Claim;
import com.claims.entity.ClaimStatus;
import com.claims.entity.ClaimType;
import com.claims.entity.Role;
import com.claims.entity.User;
import com.claims.exception.BusinessException;
import com.claims.exception.ResourceNotFoundException;
import com.claims.repository.ClaimRepository;
import com.claims.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {
    @Mock ClaimRepository claims;
    @Mock UserRepository users;
    @Mock ClaimAuditRecorder audit;
    @InjectMocks ClaimService claimService;

    private User manager;
    private User claimant;

    @BeforeEach
    void setUp() {
        manager = new User("manager@claims.local", "encoded", "Claims Manager", Role.MANAGER);
        claimant = new User("claimant@example.com", "encoded", "Account Name", Role.CLAIMANT);
    }

    @Test
    void createsClaimWithGeneratedNumberAndAllSuppliedClaimDetails() {
        when(users.findByEmailIgnoreCase("claimant@example.com")).thenReturn(Optional.of(claimant));
        when(claims.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateClaimRequest request = request("claimant@example.com", LocalDate.now(), new BigDecimal("1234.56"));

        ClaimResponse response = claimService.create(request, "claimant@example.com");

        assertNotNull(response.claimNumber());
        assertTrue(response.claimNumber().startsWith("CLM-"));
        assertEquals("POL-123", response.policyNumber());
        assertEquals("Jane Claimant", response.claimantName());
        assertEquals("claimant@example.com", response.claimantEmail());
        assertEquals(LocalDate.now(), response.incidentDate());
        assertEquals("Rear bumper damage from a parking collision.", response.description());
        assertEquals(new BigDecimal("1234.56"), response.amountClaimed());
        assertEquals(ClaimStatus.SUBMITTED, response.status());
        assertNotNull(response.createdAt());
        assertNotNull(response.updatedAt());
        verify(claims).save(argThat(saved -> saved.getClaimant() == claimant
                && saved.getClaimNumber().equals(response.claimNumber())));
    }

    @Test
    void preventsClaimantFromSubmittingOnAnotherAccount() {
        when(users.findByEmailIgnoreCase("claimant@example.com")).thenReturn(Optional.of(claimant));

        BusinessException error = assertThrows(BusinessException.class,
                () -> claimService.create(request("someone-else@example.com", LocalDate.now(), new BigDecimal("1.00")),
                        "claimant@example.com"));

        assertEquals(403, error.getStatus().value());
        verify(claims, never()).save(any());
    }

    @Test
    void allowsTheCompleteReviewInformationApprovalAndSettlementLifecycle() {
        Claim claim = new Claim("CLM-TEST", "POL-123", "Jane Claimant", "claimant@example.com",
                LocalDate.now(), "Rear bumper damage from a parking collision.", new BigDecimal("1234.56"), ClaimType.MOTOR, "SG", claimant);
        when(users.findByEmailIgnoreCase("manager@claims.local")).thenReturn(Optional.of(manager));
        when(claims.findById(4L)).thenReturn(Optional.of(claim));
        when(claims.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(ClaimStatus.UNDER_REVIEW,
                claimService.review(4L, "manager@claims.local").status());
        assertEquals(ClaimStatus.MORE_INFO_REQUIRED,
                claimService.requestInformation(4L, "Send the repair estimate", "manager@claims.local").status());
        assertEquals(ClaimStatus.UNDER_REVIEW,
                claimService.review(4L, "manager@claims.local").status());
        assertEquals(ClaimStatus.APPROVED,
                claimService.decide(4L, ClaimStatus.APPROVED, "Estimate approved", "manager@claims.local").status());
        ClaimResponse settled = claimService.decide(4L, ClaimStatus.SETTLED, "Paid", "manager@claims.local");

        assertEquals(ClaimStatus.SETTLED, settled.status());
        assertEquals("Paid", settled.decisionNotes());
        assertEquals(5, org.mockito.Mockito.mockingDetails(claims).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save")).count());
    }

    @Test
    void rejectsTransitionsThatSkipReviewAndDoesNotPersistThem() {
        Claim claim = new Claim("CLM-TEST", "POL-123", "Jane Claimant", "claimant@example.com",
                LocalDate.now(), "Rear bumper damage from a parking collision.", new BigDecimal("10.00"), ClaimType.MOTOR, "SG", claimant);
        when(users.findByEmailIgnoreCase("manager@claims.local")).thenReturn(Optional.of(manager));
        when(claims.findById(7L)).thenReturn(Optional.of(claim));

        BusinessException error = assertThrows(BusinessException.class,
                () -> claimService.decide(7L, ClaimStatus.APPROVED, "approve", "manager@claims.local"));

        assertEquals(409, error.getStatus().value());
        assertEquals("Invalid claim status transition: SUBMITTED -> APPROVED", error.getMessage());
        verify(claims, never()).save(any());
    }

    @Test
    void rejectedAndSettledClaimsAreTerminal() {
        Claim rejected = claimWithStatus(ClaimStatus.REJECTED);
        Claim settled = claimWithStatus(ClaimStatus.SETTLED);
        when(users.findByEmailIgnoreCase("manager@claims.local")).thenReturn(Optional.of(manager));
        when(claims.findById(8L)).thenReturn(Optional.of(rejected));
        when(claims.findById(9L)).thenReturn(Optional.of(settled));

        assertThrows(BusinessException.class, () -> claimService.review(8L, "manager@claims.local"));
        assertThrows(BusinessException.class, () -> claimService.review(9L, "manager@claims.local"));
        verify(claims, never()).save(any());
    }

    @Test
    void rejectsActionsWhenAuthenticatedUserNoLongerExists() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class,
                () -> claimService.create(request("missing@example.com", LocalDate.now(), BigDecimal.ONE),
                        "missing@example.com"));

        assertEquals(401, error.getStatus().value());
        verify(claims, never()).save(any());
    }

    @Test
    void preventsNonManagersFromUsingManagerOnlyClaimListing() {
        when(users.findByEmailIgnoreCase("claimant@example.com")).thenReturn(Optional.of(claimant));

        BusinessException error = assertThrows(BusinessException.class,
                () -> claimService.list("claimant@example.com", true, 0, 20));

        assertEquals(403, error.getStatus().value());
    }

    @Test
    void managerCanAssignClaimToAnOfficerAndPersistsAssignment() {
        User officer = new User("officer@example.com", "encoded", "Claims Officer", Role.OFFICER);
        Claim claim = new Claim("CLM-TEST", "POL-123", "Jane Claimant", "claimant@example.com",
                LocalDate.now(), "Rear bumper damage from a parking collision.", new BigDecimal("10.00"), ClaimType.MOTOR, "SG", claimant);
        when(users.findByEmailIgnoreCase("manager@claims.local")).thenReturn(Optional.of(manager));
        when(users.findById(12L)).thenReturn(Optional.of(officer));
        when(claims.findById(10L)).thenReturn(Optional.of(claim));
        when(claims.save(claim)).thenReturn(claim);

        ClaimResponse result = claimService.assign(10L, 12L, "manager@claims.local");

        assertEquals("Claims Officer", result.assignedOfficerName());
        verify(claims).save(claim);
    }

    @Test
    void refusesAssignmentToAUserWhoIsNotAnOfficer() {
        Claim claim = new Claim("CLM-TEST", "POL-123", "Jane Claimant", "claimant@example.com",
                LocalDate.now(), "Rear bumper damage from a parking collision.", new BigDecimal("10.00"), ClaimType.MOTOR, "SG", claimant);
        when(users.findByEmailIgnoreCase("manager@claims.local")).thenReturn(Optional.of(manager));
        when(claims.findById(10L)).thenReturn(Optional.of(claim));
        when(users.findById(13L)).thenReturn(Optional.of(claimant));

        assertThrows(ResourceNotFoundException.class,
                () -> claimService.assign(10L, 13L, "manager@claims.local"));
        verify(claims, never()).save(any());
    }

    private Claim claimWithStatus(ClaimStatus status) {
        Claim claim = new Claim("CLM-" + status, "POL-123", "Jane Claimant", "claimant@example.com",
                LocalDate.now(), "Rear bumper damage from a parking collision.", new BigDecimal("10.00"), ClaimType.MOTOR, "SG", claimant);
        claim.transitionTo(status);
        return claim;
    }

    private CreateClaimRequest request(String email, LocalDate date, BigDecimal amount) {
        return new CreateClaimRequest("POL-123", "Jane Claimant", email, date,
                "Rear bumper damage from a parking collision.", amount, ClaimType.MOTOR, "SG");
    }
}
