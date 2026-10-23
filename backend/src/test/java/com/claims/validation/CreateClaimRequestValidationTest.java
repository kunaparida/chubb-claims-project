package com.claims.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.claims.dto.AssignClaimRequest;
import com.claims.dto.CreateClaimRequest;
import com.claims.dto.NoteRequest;
import com.claims.dto.RegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreateClaimRequestValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() { factory.close(); }

    @Test
    void acceptsCompleteClaimWithPositiveAmountAndCurrentOrPastIncidentDate() {
        CreateClaimRequest request = request("POL-2026-42", "Jane Claimant", "jane@example.com",
                LocalDate.now(), "A sufficiently detailed description.", new BigDecimal("100.00"));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void requiresPolicyClaimantIdentityIncidentDateDescriptionAndPositiveAmount() {
        CreateClaimRequest request = request(" ", " ", "not-an-email", null,
                "too short", BigDecimal.ZERO);

        Set<String> invalidFields = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(invalidFields.containsAll(Set.of(
                "policyNumber", "claimantName", "claimantEmail", "incidentDate", "description", "amountClaimed")),
                "All required claim inputs should be validated; violations were " + invalidFields);
    }

    @Test
    void rejectsFutureIncidentDates() {
        CreateClaimRequest futureIncident = request("POL-1", "Jane Claimant", "jane@example.com",
                LocalDate.now().plusDays(1), "A sufficiently detailed description.", new BigDecimal("1.00"));

        var violations = validator.validate(futureIncident);
        assertTrue(violations.stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("incidentDate")),
                "Future date " + futureIncident.incidentDate() + " produced violations: " + violations);
    }

    @Test
    void rejectsNegativeAmountsAndAmountsWithMoreThanTwoFractionalDigits() {
        CreateClaimRequest negative = request("POL-1", "Jane Claimant", "jane@example.com",
                LocalDate.now(), "A sufficiently detailed description.", new BigDecimal("-1.00"));
        CreateClaimRequest fractional = request("POL-1", "Jane Claimant", "jane@example.com",
                LocalDate.now(), "A sufficiently detailed description.", new BigDecimal("1.001"));

        assertInvalidAmount(negative);
        assertInvalidAmount(fractional);
    }

    @Test
    void acceptsAmountAtMaximumIntegerPrecisionAndTrailingZeroScale() {
        CreateClaimRequest maximumIntegerDigits = request("POL-1", "Jane Claimant", "jane@example.com",
                LocalDate.now(), "A sufficiently detailed description.", new BigDecimal("999999999999.00"));
        CreateClaimRequest trailingZeroScale = request("POL-1", "Jane Claimant", "jane@example.com",
                LocalDate.now(), "A sufficiently detailed description.", new BigDecimal("1.230"));

        assertTrue(validator.validate(maximumIntegerDigits).isEmpty());
        assertTrue(validator.validate(trailingZeroScale).isEmpty());
    }

    @Test
    void rejectsAmountWithMoreThanTwelveIntegerDigits() {
        CreateClaimRequest request = request("POL-1", "Jane Claimant", "jane@example.com",
                LocalDate.now(), "A sufficiently detailed description.", new BigDecimal("1000000000000.00"));

        assertInvalidAmount(request);
    }

    @Test
    void validatesRegistrationEmailPasswordAndFullName() {
        RegisterRequest invalidRequest = new RegisterRequest("not-an-email", "short", " ");
        Set<String> invalidFields = validator.validate(invalidRequest).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(invalidFields.containsAll(Set.of("email", "password", "fullName")),
                "All invalid registration fields should be reported; violations were " + invalidFields);
    }

    @Test
    void rejectsClaimAndRegistrationFieldsAboveTheirMaximumLengths() {
        CreateClaimRequest claim = request("P".repeat(65), "N".repeat(121),
                "a".repeat(245) + "@example.com", LocalDate.now(),
                "A".repeat(4001), new BigDecimal("1.00"));
        RegisterRequest registration = new RegisterRequest(
                "a".repeat(245) + "@example.com", "P".repeat(73), "N".repeat(121));

        Set<String> claimFields = validator.validate(claim).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
        Set<String> registrationFields = validator.validate(registration).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(claimFields.containsAll(Set.of("policyNumber", "claimantName", "claimantEmail", "description")));
        assertTrue(registrationFields.containsAll(Set.of("email", "password", "fullName")));
    }

    @Test
    void validatesNoteAndOfficerAssignmentRequests() {
        assertTrue(validator.validate(new NoteRequest(" ")).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("note")));
        assertTrue(validator.validate(new NoteRequest("N".repeat(2001))).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("note")));
        assertTrue(validator.validate(new AssignClaimRequest(null)).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("officerId")));
    }

    private void assertInvalidAmount(CreateClaimRequest request) {
        assertTrue(validator.validate(request).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("amountClaimed")));
    }

    @Test
    void requiresClaimTypeAndATwoLetterMarketCode() {
        CreateClaimRequest missing = new CreateClaimRequest("POL-1", "Jane", "jane@example.com", LocalDate.now(),
                "A sufficiently detailed description.", new BigDecimal("10.00"), null, " ");
        CreateClaimRequest tooLong = new CreateClaimRequest("POL-1", "Jane", "jane@example.com", LocalDate.now(),
                "A sufficiently detailed description.", new BigDecimal("10.00"), com.claims.entity.ClaimType.MOTOR, "SGP");
        CreateClaimRequest digits = new CreateClaimRequest("POL-1", "Jane", "jane@example.com", LocalDate.now(),
                "A sufficiently detailed description.", new BigDecimal("10.00"), com.claims.entity.ClaimType.MOTOR, "S1");

        assertEquals(Set.of("claimType", "market"), fields(missing));
        assertEquals(Set.of("market"), fields(tooLong));
        assertEquals(Set.of("market"), fields(digits));
    }

    @Test
    void acceptsLowerCaseMarketCodeBecauseTheServiceNormalisesIt() {
        CreateClaimRequest request = new CreateClaimRequest("POL-1", "Jane", "jane@example.com", LocalDate.now(),
                "A sufficiently detailed description.", new BigDecimal("10.00"),
                com.claims.entity.ClaimType.PROPERTY, "hk");

        assertTrue(validator.validate(request).isEmpty());
    }

    private Set<String> fields(CreateClaimRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private CreateClaimRequest request(String policy, String name, String email, LocalDate incidentDate,
                                       String description, BigDecimal amount) {
        return new CreateClaimRequest(policy, name, email, incidentDate, description, amount,
                com.claims.entity.ClaimType.MOTOR, "SG");
    }
}
