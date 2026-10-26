package com.claims.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** A document's details, without its bytes (those are downloaded from their own endpoint). */
@Schema(description = "A document attached to a claim")
public record ClaimDocumentResponse(
        Long id,
        @Schema(description = "File name as uploaded, without any folder", example = "repair-estimate.pdf") String fileName,
        @Schema(description = "application/pdf, image/jpeg or image/png, decided from the file's contents", example = "application/pdf") String contentType,
        @Schema(description = "Size in bytes", example = "184320") long sizeBytes,
        Instant uploadedAt,
        @Schema(description = "Who attached it", example = "Jane Claimant") String uploadedByName) {}
