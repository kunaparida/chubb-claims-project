package com.claims.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A file a claimant attached to a claim (a photo, a repair estimate, a police report). The bytes live in the database next to the
 * claim, which suits the in-memory H2 database used here; a real deployment would keep them in object storage and store only a key.
 */
@Entity
@Table(name = "claim_documents",
        indexes = @Index(name = "idx_document_claim", columnList = "claim_id"))
public class ClaimDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    /** The cleaned file name (no directories), for display and for the download. */
    @Column(nullable = false, length = 255)
    private String fileName;

    /** Decided by the file's contents when it was uploaded, not by what the browser claimed. */
    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Lob
    @Column(nullable = false)
    private byte[] content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    protected ClaimDocument() {}

    public ClaimDocument(Claim claim, String fileName, String contentType, byte[] content, User uploadedBy) {
        this.claim = claim;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = content.length;
        this.content = content;
        this.uploadedBy = uploadedBy;
    }

    public Long getId() { return id; }
    public Claim getClaim() { return claim; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public byte[] getContent() { return content; }
    public User getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
}
