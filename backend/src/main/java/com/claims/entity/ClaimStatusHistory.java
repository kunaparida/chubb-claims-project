package com.claims.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only audit record: one row per claim status change (including the initial submission). */
@Entity
@Table(name = "claim_status_history",
        indexes = @Index(name = "idx_history_claim", columnList = "claim_id"))
public class ClaimStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    /** Null for the initial SUBMITTED record. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_id", nullable = false)
    private User changedBy;

    @Column(length = 2000)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant changedAt = Instant.now();

    protected ClaimStatusHistory() {}

    public ClaimStatusHistory(Claim claim, ClaimStatus fromStatus, ClaimStatus toStatus, User changedBy, String note) {
        this.claim = claim;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.note = note;
    }

    public Long getId() { return id; }
    public Claim getClaim() { return claim; }
    public ClaimStatus getFromStatus() { return fromStatus; }
    public ClaimStatus getToStatus() { return toStatus; }
    public User getChangedBy() { return changedBy; }
    public String getNote() { return note; }
    public Instant getChangedAt() { return changedAt; }
}
