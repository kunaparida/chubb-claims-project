package com.claims.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claims.event.OutboxPublisher;
import com.claims.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
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

/**
 * End-to-end test of the features added for the assessment brief: claimant information flow, officer queue and
 * pick-up, status filter, audit history, outbox events and the liability breakdown.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClaimLifecycleExtensionsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired OutboxEventRepository outboxEvents;

    @Test
    void claimantInformationFlowQueueHistoryAndEventsWorkEndToEnd() throws Exception {
        String email = "claimant-" + UUID.randomUUID() + "@example.com";
        String claimant = register(email);
        String officer = login("officer@claims.local");
        String manager = login("manager@claims.local");
        long officerId = objectMapper.readTree(mvc.perform(get("/api/auth/me").header("Authorization", bearer(officer)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        // 1. submit a PROPERTY claim for the HK market (market code is normalised to upper case)
        MvcResult created = mvc.perform(post("/api/claims").header("Authorization", bearer(claimant))
                        .contentType(MediaType.APPLICATION_JSON).content(json(claimBody(email, "PROPERTY", "hk"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimType").value("PROPERTY"))
                .andExpect(jsonPath("$.market").value("HK"))
                .andReturn();
        String id = value(created, "id");

        // 2. liability is broken down by line of business and market
        mvc.perform(get("/api/dashboard/liability").header("Authorization", bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byClaimType[?(@.key=='PROPERTY')].pendingExposure", hasItem(750.00)))
                .andExpect(jsonPath("$.byMarket[?(@.key=='HK')].pendingExposure", hasItem(750.00)));

        // 3. the claim waits in the officer queue; claimants cannot read the queue
        mvc.perform(get("/api/claims/queue").header("Authorization", bearer(claimant)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/claims/queue").header("Authorization", bearer(officer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(Integer.valueOf(id))));

        // 4. the officer can read the unassigned claim, picks it up, and it leaves the queue
        mvc.perform(get("/api/claims/" + id).header("Authorization", bearer(officer))).andExpect(status().isOk());
        mvc.perform(put("/api/claims/" + id + "/pickup").header("Authorization", bearer(claimant)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/claims/" + id + "/pickup").header("Authorization", bearer(officer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedOfficerId").value(Long.valueOf(officerId).intValue()));
        mvc.perform(get("/api/claims/queue").header("Authorization", bearer(officer)))
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.not(hasItem(Integer.valueOf(id)))));

        // 5. review -> request info; the claimant cannot answer before info is requested, then can
        mvc.perform(put("/api/claims/" + id + "/provide-info").header("Authorization", bearer(claimant))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("response", "too early"))))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/claims/" + id + "/review").header("Authorization", bearer(officer)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/claims/" + id + "/request-info").header("Authorization", bearer(officer))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("note", "Send the repair estimate"))))
                .andExpect(jsonPath("$.status").value("MORE_INFO_REQUIRED"));
        mvc.perform(put("/api/claims/" + id + "/provide-info").header("Authorization", bearer(officer))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("response", "x"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/claims/" + id + "/provide-info").header("Authorization", bearer(claimant))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.response").exists());
        mvc.perform(put("/api/claims/" + id + "/provide-info").header("Authorization", bearer(claimant))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("response", "Estimate: 900 HKD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.informationRequest").value("Send the repair estimate"))
                .andExpect(jsonPath("$.informationResponse").value("Estimate: 900 HKD"));

        // 6. decision and settlement
        mvc.perform(put("/api/claims/" + id + "/approve").header("Authorization", bearer(officer))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("note", "Within cover"))))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(put("/api/claims/" + id + "/settle").header("Authorization", bearer(officer))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("note", "Paid"))))
                .andExpect(jsonPath("$.status").value("SETTLED"));

        // 7. full audit trail, visible to the claimant, hidden from other claimants
        mvc.perform(get("/api/claims/" + id + "/history").header("Authorization", bearer(claimant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$[2].toStatus").value("MORE_INFO_REQUIRED"))
                .andExpect(jsonPath("$[2].note").value("Send the repair estimate"))
                .andExpect(jsonPath("$[3].changedByName").value("Jane Claimant")) // claimant's own answer
                .andExpect(jsonPath("$[5].toStatus").value("SETTLED"));
        String otherClaimant = register("other-" + UUID.randomUUID() + "@example.com");
        mvc.perform(get("/api/claims/" + id + "/history").header("Authorization", bearer(otherClaimant)))
                .andExpect(status().isNotFound());

        // 8. status filter on the list
        mvc.perform(get("/api/claims?status=SETTLED").header("Authorization", bearer(officer)))
                .andExpect(jsonPath("$.content[*].id", hasItem(Integer.valueOf(id))));
        mvc.perform(get("/api/claims?status=APPROVED").header("Authorization", bearer(officer)))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/claims?status=NOT_A_STATUS").header("Authorization", bearer(officer)))
                .andExpect(status().isBadRequest());

        // 9. every change produced an outbox event (same transaction); the relay publishes and marks them
        assertEquals(6, outboxEvents.countByPublishedAtIsNull());
        assertEquals(6, outboxPublisher.publishPending());
        assertEquals(0, outboxEvents.countByPublishedAtIsNull());
        assertEquals(0, outboxPublisher.publishPending());
    }

    @Test
    void claimTypeAndMarketAreValidated() throws Exception {
        String email = "claimant-" + UUID.randomUUID() + "@example.com";
        String claimant = register(email);
        Map<String, Object> body = claimBody(email, "MOTOR", "SGP");
        body.remove("claimType");

        mvc.perform(post("/api/claims").header("Authorization", bearer(claimant))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.claimType").exists())
                .andExpect(jsonPath("$.errors.market").exists());
    }

    private Map<String, Object> claimBody(String email, String type, String market) {
        Map<String, Object> body = new HashMap<>();
        body.put("policyNumber", "POL-2026-777");
        body.put("claimantName", "Jane Claimant");
        body.put("claimantEmail", email);
        body.put("incidentDate", LocalDate.now().toString());
        body.put("description", "Water damage to the kitchen from a burst pipe.");
        body.put("amountClaimed", new java.math.BigDecimal("750.00"));
        body.put("claimType", type);
        body.put("market", market);
        return body;
    }

    private String register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Claimant123!", "fullName", "Jane Claimant"))))
                .andExpect(status().isCreated()).andReturn();
        return value(result, "token");
    }

    private String login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "ChangeMe123!"))))
                .andExpect(status().isOk()).andReturn();
        return value(result, "token");
    }

    private String value(MvcResult result, String key) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(key).asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
}
