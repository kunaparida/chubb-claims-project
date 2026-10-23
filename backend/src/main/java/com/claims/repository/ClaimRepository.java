package com.claims.repository;

import com.claims.entity.Claim;
import com.claims.entity.ClaimStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    Page<Claim> findByClaimantId(Long claimantId, Pageable pageable);
    Page<Claim> findByAssignedOfficerId(Long officerId, Pageable pageable);
    long countByAssignedOfficerIdAndStatusIn(Long officerId, List<ClaimStatus> statuses);
    long countByAssignedOfficerId(Long officerId);
    long countByStatus(ClaimStatus status);
    long countByAssignedOfficerIsNull();

    Page<Claim> findByStatus(ClaimStatus status, Pageable pageable);
    Page<Claim> findByClaimantIdAndStatus(Long claimantId, ClaimStatus status, Pageable pageable);
    Page<Claim> findByAssignedOfficerIdAndStatus(Long officerId, ClaimStatus status, Pageable pageable);

    /** Officer work queue: claims nobody has picked up yet. */
    Page<Claim> findByAssignedOfficerIsNullAndStatus(ClaimStatus status, Pageable pageable);

    /** Rows of [claimType, status, sum(amount)] used for the liability breakdown. */
    @Query("select c.claimType, c.status, sum(c.amountClaimed) from Claim c "
            + "where c.status in :statuses group by c.claimType, c.status")
    List<Object[]> sumAmountByClaimTypeAndStatus(@Param("statuses") List<ClaimStatus> statuses);

    /** Rows of [market, status, sum(amount)] used for the liability breakdown. */
    @Query("select c.market, c.status, sum(c.amountClaimed) from Claim c "
            + "where c.status in :statuses group by c.market, c.status")
    List<Object[]> sumAmountByMarketAndStatus(@Param("statuses") List<ClaimStatus> statuses);

    @Query("select coalesce(sum(c.amountClaimed), 0) from Claim c where c.status in :statuses")
    java.math.BigDecimal sumAmountByStatusIn(@Param("statuses") List<ClaimStatus> statuses);

    @Query("select c.status, count(c) from Claim c group by c.status")
    List<Object[]> countGroupedByStatus();
}
