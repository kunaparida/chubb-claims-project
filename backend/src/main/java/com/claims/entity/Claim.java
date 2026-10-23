package com.claims.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "claims", uniqueConstraints = @UniqueConstraint(name = "uk_claim_number", columnNames = "claim_number"))
public class Claim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true, length = 32)
    private String claimNumber;

    @Column(nullable = false, length = 64)
    private String policyNumber;

    @Column(nullable = false, length = 120)
    private String claimantName;

    @Column(nullable = false, length = 254)
    private String claimantEmail;

    @Column(nullable = false)
    private java.time.LocalDate incidentDate;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amountClaimed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimType claimType;

    /** ISO 3166-1 alpha-2 code of the market the claim belongs to (e.g. SG, HK, AU). */
    @Column(nullable = false, length = 2)
    private String market;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimStatus status = ClaimStatus.SUBMITTED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claimant_id", nullable = false)
    private User claimant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_officer_id")
    private User assignedOfficer;

    @Column(length = 2000)
    private String informationRequest;

    @Column(length = 2000)
    private String informationResponse;

    @Column(length = 2000)
    private String decisionNotes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected Claim() {}

    public Claim(String claimNumber, String policyNumber, String claimantName, String claimantEmail,
                 java.time.LocalDate incidentDate, String description, BigDecimal amountClaimed,
                 ClaimType claimType, String market, User claimant) {
        this.claimNumber = claimNumber;
        this.policyNumber = policyNumber;
        this.claimantName = claimantName;
        this.claimantEmail = claimantEmail;
        this.incidentDate = incidentDate;
        this.description = description;
        this.amountClaimed = amountClaimed;
        this.claimType = claimType;
        this.market = market;
        this.claimant = claimant;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public void assign(User officer) { assignedOfficer = officer; }
    public void transitionTo(ClaimStatus next) { status = next; }
    public void setInformationRequest(String informationRequest) { this.informationRequest = informationRequest; }
    public void setInformationResponse(String informationResponse) { this.informationResponse = informationResponse; }
    public void setDecisionNotes(String decisionNotes) { this.decisionNotes = decisionNotes; }

    public Long getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public String getPolicyNumber() { return policyNumber; }
    public String getClaimantName() { return claimantName; }
    public String getClaimantEmail() { return claimantEmail; }
    public java.time.LocalDate getIncidentDate() { return incidentDate; }
    public String getDescription() { return description; }
    public BigDecimal getAmountClaimed() { return amountClaimed; }
    public ClaimStatus getStatus() { return status; }
    public User getClaimant() { return claimant; }
    public User getAssignedOfficer() { return assignedOfficer; }
    public ClaimType getClaimType() { return claimType; }
    public String getMarket() { return market; }
    public String getInformationRequest() { return informationRequest; }
    public String getInformationResponse() { return informationResponse; }
    public String getDecisionNotes() { return decisionNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
