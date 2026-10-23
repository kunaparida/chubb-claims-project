package com.claims.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClaimsApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void rejectsRegistrationRequestsThatAreNotJson() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"email\":\"jane.doe@example.com\",\"password\":\"SecurePass123!\",\"fullName\":\"Jane Doe\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Content-Type must be application/json"));
    }

    @Test
    void reportsInvalidRegistrationFieldsAndRequiresAuthenticationForProfile() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "invalid-email", "password", "short", "fullName", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists());

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void supportsAuthenticatedClaimantOfficerAndManagerWorkflowsAndProblemResponses() throws Exception {
        mvc.perform(get("/api/claims"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        String claimantEmail = "claimant-" + UUID.randomUUID() + "@example.com";
        MvcResult registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", claimantEmail, "password", "Claimant123!", "fullName", "Jane Claimant"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLAIMANT"))
                .andReturn();
        String claimantToken = value(registration, "token");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", claimantEmail, "password", "Claimant123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", claimantEmail, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", claimantEmail, "password", "Claimant123!", "fullName", "Jane Claimant"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(claimantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(claimantEmail));

        Map<String, Object> validClaim = claimBody(claimantEmail, "POL-2026-00042",
                "Rear bumper damage from a parking collision.", "1250.00", LocalDate.now().toString());
        String validBody = json(validClaim);
        String claimsPath = "/api/claims";

        mvc.perform(post(claimsPath).header("Authorization", bearer(claimantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(claimBody(claimantEmail, " ", "short", "0", LocalDate.now().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.policyNumber").exists())
                .andExpect(jsonPath("$.errors.description").exists())
                .andExpect(jsonPath("$.errors.amountClaimed").exists());

        mvc.perform(post(claimsPath).header("Authorization", bearer(claimantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(claimBody(claimantEmail, "POL-FUTURE", "A sufficiently detailed description.",
                                "10.00", LocalDate.now().plusDays(1).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.incidentDate").exists());

        mvc.perform(post(claimsPath).header("Authorization", bearer(claimantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(claimBody("other@example.com", "POL-SPOOF", "A sufficiently detailed description.",
                                "10.00", LocalDate.now().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        MvcResult createResult = mvc.perform(post(claimsPath).header("Authorization", bearer(claimantToken))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimNumber").value(org.hamcrest.Matchers.startsWith("CLM-")))
                .andExpect(jsonPath("$.policyNumber").value("POL-2026-00042"))
                .andExpect(jsonPath("$.claimantName").value("Jane Claimant"))
                .andExpect(jsonPath("$.claimantEmail").value(claimantEmail))
                .andExpect(jsonPath("$.incidentDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.amountClaimed").value(1250.00))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();
        String claimId = value(createResult, "id");

        MvcResult rejectedClaimResult = mvc.perform(post(claimsPath).header("Authorization", bearer(claimantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(claimBody(claimantEmail, "POL-2026-00043",
                                "Another sufficiently detailed incident description.", "25.50", LocalDate.now().toString()))))
                .andExpect(status().isCreated())
                .andReturn();
        String rejectedClaimId = value(rejectedClaimResult, "id");

        mvc.perform(get(claimsPath + "/" + claimId).header("Authorization", bearer(claimantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Rear bumper damage from a parking collision."));
        mvc.perform(get(claimsPath + "/999999").header("Authorization", bearer(claimantToken)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(claimsPath).header("Authorization", bearer(claimantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].claimNumber", hasItem(value(createResult, "claimNumber"))))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
        mvc.perform(get(claimsPath + "?page=0&size=1").header("Authorization", bearer(claimantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(get("/api/dashboard/workload").header("Authorization", bearer(claimantToken)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        String managerToken = login("manager@claims.local");
        String officerToken = login("officer@claims.local");
        JsonNode officerProfile = objectMapper.readTree(mvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(officerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String officerId = officerProfile.get("id").asText();

        mvc.perform(post("/api/claims/" + claimId + "/review")
                        .header("Authorization", bearer(officerToken)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405));

        mvc.perform(get("/api/dashboard/workload").header("Authorization", bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(2));
        mvc.perform(get("/api/dashboard/liability").header("Authorization", bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingExposure").value(1275.50));
        mvc.perform(get("/api/dashboard/performance").header("Authorization", bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].officerId", hasItem(Integer.valueOf(officerId))));

        assign(managerToken, claimId, officerId);
        assign(managerToken, rejectedClaimId, officerId);
        mvc.perform(get(claimsPath).header("Authorization", bearer(officerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        workflowPut(officerToken, claimId, "review", null, ClaimJsonExpectation.UNDER_REVIEW);
        workflowPut(officerToken, claimId, "request-info", Map.of("note", "Send the repair estimate"),
                ClaimJsonExpectation.MORE_INFO_REQUIRED);
        mvc.perform(get(claimsPath + "/" + claimId).header("Authorization", bearer(officerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.informationRequest").value("Send the repair estimate"));
        workflowPut(officerToken, claimId, "review", null, ClaimJsonExpectation.UNDER_REVIEW);
        workflowPut(officerToken, claimId, "approve", Map.of("note", "Estimate approved"),
                ClaimJsonExpectation.APPROVED);
        workflowPut(officerToken, claimId, "settle", Map.of("note", "Payment issued"),
                ClaimJsonExpectation.SETTLED);
        mvc.perform(put("/api/claims/" + claimId + "/review")
                        .header("Authorization", bearer(officerToken)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid claim status transition: SETTLED -> UNDER_REVIEW"));

        workflowPut(officerToken, rejectedClaimId, "review", null, ClaimJsonExpectation.UNDER_REVIEW);
        workflowPut(officerToken, rejectedClaimId, "reject", Map.of("note", "Not covered"),
                ClaimJsonExpectation.REJECTED);
        mvc.perform(get("/api/dashboard/workload").header("Authorization", bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settled").value(1))
                .andExpect(jsonPath("$.rejected").value(1));
    }

    private void assign(String managerToken, String claimId, String officerId) throws Exception {
        mvc.perform(put("/api/claims/" + claimId + "/assign")
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("officerId", officerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedOfficerId").value(Integer.valueOf(officerId)));
    }

    private void workflowPut(String token, String claimId, String action, Object body,
                             ClaimJsonExpectation expectation) throws Exception {
        var request = put("/api/claims/" + claimId + "/" + action)
                .header("Authorization", bearer(token));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectation.name()));
    }

    private String login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "ChangeMe123!"))))
                .andExpect(status().isOk()).andReturn();
        return value(result, "token");
    }

    private String value(MvcResult result, String key) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(key).asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }

    private Map<String, Object> claimBody(String email, String policy, String description, String amount, String incidentDate) {
        return Map.of("policyNumber", policy, "claimantName", "Jane Claimant", "claimantEmail", email,
                "incidentDate", incidentDate, "description", description, "amountClaimed", new java.math.BigDecimal(amount),
                "claimType", "MOTOR", "market", "SG");
    }

    private enum ClaimJsonExpectation {
        UNDER_REVIEW, MORE_INFO_REQUIRED, APPROVED, REJECTED, SETTLED
    }
}
