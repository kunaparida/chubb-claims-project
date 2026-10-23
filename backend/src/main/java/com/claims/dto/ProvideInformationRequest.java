package com.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The claimant's answer to an officer's information request. */
public record ProvideInformationRequest(@NotBlank @Size(max = 2000) String response) {}
