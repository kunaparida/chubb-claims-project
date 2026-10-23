package com.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.claims.entity.ClaimType;
import com.claims.validation.ValidClaimAmount;
import com.claims.validation.NotFutureDate;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateClaimRequest(
        @NotBlank @Size(max = 64) String policyNumber,
        @NotBlank @Size(max = 120) String claimantName,
        @NotBlank @Email @Size(max = 254) String claimantEmail,
        @NotNull @NotFutureDate LocalDate incidentDate,
        @NotBlank @Size(min = 20, max = 4000) String description,
        @NotNull @ValidClaimAmount BigDecimal amountClaimed,
        @NotNull ClaimType claimType,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a 2-letter market code such as SG") String market) {}
