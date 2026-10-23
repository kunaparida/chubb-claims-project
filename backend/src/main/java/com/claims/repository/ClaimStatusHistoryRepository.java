package com.claims.repository;

import com.claims.entity.ClaimStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimStatusHistoryRepository extends JpaRepository<ClaimStatusHistory, Long> {
    List<ClaimStatusHistory> findByClaimIdOrderByChangedAtAscIdAsc(Long claimId);
}
