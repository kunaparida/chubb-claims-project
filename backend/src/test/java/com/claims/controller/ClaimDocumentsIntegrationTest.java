package com.claims.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Attaching, listing and downloading documents through the real security chain, database and error handling. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClaimDocumentsIntegrationTest {
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.UTF_8);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    private String claimant;
    private String otherClaimant;
    private String officer;
    private String manager;
    private long claimId;

    @BeforeEach
    void setUp() throws Exception {
        claimant = register();
        otherClaimant = register();
        officer = login("officer@claims.local");
        manager = login("manager@claims.local");
        claimId = createClaim(claimant);
    }

    @Test
    void theClaimantAttachesADocumentAndStaffCanListAndDownloadIt() throws Exception {
        mvc.perform(upload(claimId, claimant, "repair-estimate.pdf", PDF))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/claims/" + claimId + "/documents/\\d+")))
                .andExpect(jsonPath("$.fileName").value("repair-estimate.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(PDF.length))
                .andExpect(jsonPath("$.uploadedByName").value("Jane Claimant"));

        for (String token : new String[] {claimant, officer, manager}) {
            mvc.perform(get("/api/claims/{id}/documents", claimId).header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].fileName").value("repair-estimate.pdf"));
        }

        long documentId = firstDocumentId(claimant);
        for (String token : new String[] {claimant, officer, manager}) {
            mvc.perform(get("/api/claims/{id}/documents/{doc}", claimId, documentId).header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf"))
                    .andExpect(content().bytes(PDF))
                    .andExpect(header().string("Content-Disposition", containsString("attachment")))
                    .andExpect(header().string("Content-Disposition", containsString("repair-estimate.pdf")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
    }

    @Test
    void sendsASimpleFileNamePlainAndAnAccentedOneEncoded() throws Exception {
        mvc.perform(upload(claimId, claimant, "repair-estimate.pdf", PDF)).andExpect(status().isCreated());
        mvc.perform(upload(claimId, claimant, "résumé.pdf", PDF)).andExpect(status().isCreated());
        MvcResult list = mvc.perform(get("/api/claims/{id}/documents", claimId).header("Authorization", bearer(claimant))).andReturn();
        var details = objectMapper.readTree(list.getResponse().getContentAsString());

        mvc.perform(get("/api/claims/{id}/documents/{doc}", claimId, details.get(0).get("id").asLong()).header("Authorization", bearer(claimant)))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"repair-estimate.pdf\""));
        mvc.perform(get("/api/claims/{id}/documents/{doc}", claimId, details.get(1).get("id").asLong()).header("Authorization", bearer(claimant)))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''r%C3%A9sum%C3%A9.pdf")));
    }

    @Test
    void aClaimWithNoDocumentsHasAnEmptyList() throws Exception {
        mvc.perform(get("/api/claims/{id}/documents", claimId).header("Authorization", bearer(claimant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anotherClaimantCannotSeeAttachOrDownloadAndLearnsNothingAboutTheClaim() throws Exception {
        mvc.perform(upload(claimId, claimant, "estimate.pdf", PDF)).andExpect(status().isCreated());
        long documentId = firstDocumentId(claimant);

        mvc.perform(get("/api/claims/{id}/documents", claimId).header("Authorization", bearer(otherClaimant)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/claims/{id}/documents/{doc}", claimId, documentId).header("Authorization", bearer(otherClaimant)))
                .andExpect(status().isNotFound());
        mvc.perform(upload(claimId, otherClaimant, "mine.pdf", PDF)).andExpect(status().isNotFound());
    }

    @Test
    void staffCannotAttachDocumentsAndAnonymousCallersAreTurnedAway() throws Exception {
        mvc.perform(upload(claimId, officer, "estimate.pdf", PDF)).andExpect(status().isForbidden());
        mvc.perform(upload(claimId, manager, "estimate.pdf", PDF)).andExpect(status().isForbidden());

        mvc.perform(get("/api/claims/{id}/documents", claimId)).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/claims/{id}/documents", claimId).file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aDocumentIsOnlyReachableThroughTheClaimItBelongsTo() throws Exception {
        mvc.perform(upload(claimId, claimant, "estimate.pdf", PDF)).andExpect(status().isCreated());
        long documentId = firstDocumentId(claimant);
        long otherClaimId = createClaim(claimant);

        mvc.perform(get("/api/claims/{id}/documents/{doc}", otherClaimId, documentId).header("Authorization", bearer(claimant)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Document not found"));
        mvc.perform(get("/api/claims/{id}/documents/{doc}", claimId, documentId + 999).header("Authorization", bearer(claimant)))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesFilesThatAreNotAcceptedAndExplainsWhy() throws Exception {
        mvc.perform(upload(claimId, claimant, "notes.pdf", "plain text, not a pdf".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("\"notes.pdf\" is not a PDF, JPG or PNG file"));
        mvc.perform(upload(claimId, claimant, "empty.pdf", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("\"empty.pdf\" is empty"));
        mvc.perform(upload(claimId, claimant, "photo.png", PDF))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value(containsString("PDF file but its name says otherwise")));
        mvc.perform(get("/api/claims/{id}/documents", claimId).header("Authorization", bearer(claimant)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void answersClearlyWhenTheFilePartIsMissingOrTheRequestIsNotMultipart() throws Exception {
        mvc.perform(multipart("/api/claims/{id}/documents", claimId).file(new MockMultipartFile("other", "a.pdf", "application/pdf", PDF))
                        .header("Authorization", bearer(claimant)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("\"file\" field")));

        mvc.perform(post("/api/claims/{id}/documents", claimId).header("Authorization", bearer(claimant))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("Content-Type must be multipart/form-data"));
    }

    @Test
    void stopsAtTheMaximumNumberOfDocuments() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mvc.perform(upload(claimId, claimant, "page-" + i + ".pdf", PDF)).andExpect(status().isCreated());
        }

        mvc.perform(upload(claimId, claimant, "page-6.pdf", PDF))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A claim can have at most 5 documents"));
        mvc.perform(get("/api/claims/{id}/documents", claimId).header("Authorization", bearer(claimant)))
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void aRejectedClaimTakesNoMoreDocumentsButKeepsTheOnesItHas() throws Exception {
        mvc.perform(upload(claimId, claimant, "before.pdf", PDF)).andExpect(status().isCreated());
        mvc.perform(put("/api/claims/{id}/pickup", claimId).header("Authorization", bearer(officer))).andExpect(status().isOk());
        mvc.perform(put("/api/claims/{id}/review", claimId).header("Authorization", bearer(officer))).andExpect(status().isOk());
        mvc.perform(put("/api/claims/{id}/reject", claimId).header("Authorization", bearer(officer))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("note", "Not covered by the policy."))))
                .andExpect(status().isOk());

        mvc.perform(upload(claimId, claimant, "after.pdf", PDF))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("rejected")));
        mvc.perform(get("/api/claims/{id}/documents", claimId).header("Authorization", bearer(claimant)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(long id, String token, String name, byte[] bytes) {
        return multipart("/api/claims/{id}/documents", id)
                .file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
                .header("Authorization", bearer(token));
    }

    private long firstDocumentId(String token) throws Exception {
        MvcResult list = mvc.perform(get("/api/claims/{id}/documents", claimId).header("Authorization", bearer(token))).andReturn();
        return objectMapper.readTree(list.getResponse().getContentAsString()).get(0).get("id").asLong();
    }

    private String register() throws Exception {
        String email = "claimant-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Claimant123!", "fullName", "Jane Claimant"))))
                .andExpect(status().isCreated()).andReturn();
        return token(result);
    }

    private String login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "ChangeMe123!"))))
                .andExpect(status().isOk()).andReturn();
        return token(result);
    }

    private long createClaim(String token) throws Exception {
        MvcResult me = mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andReturn();
        String email = objectMapper.readTree(me.getResponse().getContentAsString()).get("email").asText();
        Map<String, Object> body = Map.of("policyNumber", "POL-2026-00042", "claimantName", "Jane Claimant", "claimantEmail", email,
                "incidentDate", LocalDate.now().toString(), "description", "Rear bumper damaged in a car park collision.",
                "amountClaimed", new BigDecimal("1250.00"), "claimType", "MOTOR", "market", "SG");
        MvcResult created = mvc.perform(post("/api/claims").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private String token(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
}
