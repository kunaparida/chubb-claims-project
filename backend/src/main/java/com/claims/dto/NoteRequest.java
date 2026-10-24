package com.claims.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The reason or question recorded with a status change")
public record NoteRequest(
        @Schema(description = "Required, at most 2000 characters", example = "Please send the repair estimate.")
        @NotBlank @Size(max = 2000) String note) {}
