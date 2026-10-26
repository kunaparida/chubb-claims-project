package com.claims.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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

/** Facts about the API as a whole: how a created claim is addressed, what is public, and that the documentation is complete. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void createdClaimIsAddressedByTheLocationHeader() throws Exception {
        String email = "claimant-" + UUID.randomUUID() + "@example.com";
        MvcResult registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "Claimant123!", "fullName", "Jane Claimant"))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(registration.getResponse().getContentAsString()).get("token").asText();

        Map<String, Object> claim = Map.of("policyNumber", "POL-2026-00042", "claimantName", "Jane Claimant",
                "claimantEmail", email, "incidentDate", java.time.LocalDate.now().toString(),
                "description", "Rear bumper damaged in a car park collision.",
                "amountClaimed", new BigDecimal("1250.00"), "claimType", "MOTOR", "market", "SG");

        MvcResult created = mvc.perform(post("/api/claims")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(claim)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/claims/\\d+")))
                .andReturn();

        String location = created.getResponse().getHeader("Location");
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        assertTrue(location.endsWith("/api/claims/" + id), "Location must point at the new claim: " + location);

        mvc.perform(get("/api/claims/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void h2ConsoleIsNotReachableUnlessSwitchedOn() throws Exception {
        mvc.perform(get("/h2-console/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyEndpointIsDocumentedWithASummaryAndTheApiIsDescribed() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode doc = objectMapper.readTree(result.getResponse().getContentAsString());

        List<String> undocumented = new ArrayList<>();
        int operations = 0;
        for (var path : doc.get("paths").properties()) {
            for (var method : path.getValue().properties()) {
                operations++;
                String summary = method.getValue().path("summary").asText("");
                if (summary.isBlank()) {
                    undocumented.add(method.getKey().toUpperCase() + " " + path.getKey());
                }
            }
        }

        assertEquals(22, operations, "the API has 22 endpoints; update this test when one is added or removed");
        assertTrue(undocumented.isEmpty(), "endpoints without a summary: " + undocumented);

        List<String> tags = new ArrayList<>();
        doc.get("tags").forEach(tag -> tags.add(tag.get("name").asText()));
        assertTrue(tags.containsAll(List.of("Authentication", "Claims", "Documents", "Manager dashboard")), "tags: " + tags);
        assertFalse(doc.at("/components/securitySchemes/bearerAuth").isMissingNode(), "bearer scheme must be declared");
    }
}
