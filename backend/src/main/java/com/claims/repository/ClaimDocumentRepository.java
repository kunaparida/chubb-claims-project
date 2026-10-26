package com.claims.repository;

import com.claims.dto.ClaimDocumentResponse;
import com.claims.entity.ClaimDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {
    long countByClaimId(Long claimId);

    Optional<ClaimDocument> findByIdAndClaimId(Long id, Long claimId);

    /** Details only, oldest first: listing a claim's documents must not load the bytes of every file. */
    @Query("select new com.claims.dto.ClaimDocumentResponse(d.id, d.fileName, d.contentType, d.sizeBytes, d.uploadedAt, u.fullName) "
            + "from ClaimDocument d join d.uploadedBy u where d.claim.id = :claimId order by d.id")
    List<ClaimDocumentResponse> findDetailsByClaimId(@Param("claimId") Long claimId);
}
