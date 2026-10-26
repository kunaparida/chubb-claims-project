package com.claims.controller;

import com.claims.dto.ClaimDocumentResponse;
import com.claims.service.ClaimDocumentService;
import com.claims.service.ClaimDocumentService.StoredDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/claims/{claimId}/documents")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Documents", description = "Optional supporting files for a claim: photos, estimates, reports (PDF, JPG or PNG; at most 5 per claim, 5 MB each). "
        + "Whoever may see a claim may list and download its documents; only the claimant who owns it can add them.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token"),
        @ApiResponse(responseCode = "404", description = "No such claim, or it is not visible to you (existence is not revealed)")
})
public class ClaimDocumentController {
    private final ClaimDocumentService documents;

    public ClaimDocumentController(ClaimDocumentService documents) { this.documents = documents; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLAIMANT')")
    @Operation(summary = "Attach a document to a claim",
            description = "Claimants only, on their own claim, and only while it is open (not rejected or settled). Send one file per call as "
                    + "multipart/form-data in the `file` field. The type is decided from the file's contents, not its name.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Attached; the Location header points at it"),
            @ApiResponse(responseCode = "400", description = "No file, an empty file, or a missing or over-long name"),
            @ApiResponse(responseCode = "403", description = "Only the claimant can attach documents"),
            @ApiResponse(responseCode = "409", description = "The claim is closed, or already has the maximum number of documents"),
            @ApiResponse(responseCode = "413", description = "The file is larger than the limit"),
            @ApiResponse(responseCode = "415", description = "Not a PDF, JPG or PNG, or the name and the contents disagree")
    })
    public ResponseEntity<ClaimDocumentResponse> upload(@PathVariable Long claimId,
                                                        @Parameter(description = "The file to attach") @RequestPart("file") MultipartFile file,
                                                        @AuthenticationPrincipal UserDetails user) {
        ClaimDocumentResponse created = documents.add(claimId, file, user.getUsername());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLAIMANT','OFFICER','MANAGER')")
    @Operation(summary = "List a claim's documents",
            description = "Details only (name, type, size, who and when), oldest first. Download each file from its own address.")
    public List<ClaimDocumentResponse> list(@PathVariable Long claimId, @AuthenticationPrincipal UserDetails user) {
        return documents.list(claimId, user.getUsername());
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('CLAIMANT','OFFICER','MANAGER')")
    @Operation(summary = "Download a document",
            description = "The file itself, sent as an attachment so a browser saves it instead of running it.")
    @ApiResponse(responseCode = "200", description = "The file's bytes")
    public ResponseEntity<byte[]> download(@PathVariable Long claimId, @PathVariable Long documentId,
                                           @AuthenticationPrincipal UserDetails user) {
        StoredDocument document = documents.download(claimId, documentId, user.getUsername());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .contentLength(document.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(document.fileName()).toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(document.content());
    }

    /** A plain `filename="x.pdf"` when the name is simple ASCII; the encoded form only for names that need it (accents, other scripts). */
    private static ContentDisposition attachment(String fileName) {
        boolean plain = fileName.chars().allMatch(c -> c >= 0x20 && c < 0x7F && c != '"' && c != '\\');
        return plain
                ? ContentDisposition.attachment().filename(fileName).build()
                : ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build();
    }
}
