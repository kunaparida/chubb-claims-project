package com.claims.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Who the claim is assigned to")
public record AssignClaimRequest(
        @Schema(description = "Id of an officer (see /api/dashboard/performance for the list)", example = "2")
        @NotNull Long officerId) {}
