package com.claims.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The claimant's answer to an officer's information request. */
@Schema(description = "The claimant's answer to an officer's information request")
public record ProvideInformationRequest(
        @Schema(description = "Required, at most 2000 characters", example = "The repair estimate is attached to the email I sent.")
        @NotBlank @Size(max = 2000) String response) {}
