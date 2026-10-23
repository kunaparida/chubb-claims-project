package com.claims.service;

import com.claims.dto.LiabilityResponse;
import com.claims.dto.OfficerPerformance;
import com.claims.dto.WorkloadResponse;
import com.claims.entity.ClaimStatus;
import com.claims.entity.Role;
import com.claims.exception.BusinessException;
import com.claims.repository.ClaimRepository;
import com.claims.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagerDashboardService {
    private final ClaimRepository claims;
    private final UserRepository users;

    public ManagerDashboardService(ClaimRepository claims, UserRepository users) {
        this.claims = claims;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public WorkloadResponse workload() {
        long submitted = claims.countByStatus(ClaimStatus.SUBMITTED);
        long review = claims.countByStatus(ClaimStatus.UNDER_REVIEW);
        long info = claims.countByStatus(ClaimStatus.MORE_INFO_REQUIRED);
        long approved = claims.countByStatus(ClaimStatus.APPROVED);
        long rejected = claims.countByStatus(ClaimStatus.REJECTED);
        long settled = claims.countByStatus(ClaimStatus.SETTLED);
        long unassigned = claims.countByAssignedOfficerIsNull();
        return new WorkloadResponse(unassigned, submitted + review + info + approved,
                submitted, review, info, approved, rejected, settled);
    }

    @Transactional(readOnly = true)
    public LiabilityResponse liability() {
        BigDecimal pending = claims.sumAmountByStatusIn(List.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW,
                ClaimStatus.MORE_INFO_REQUIRED));
        BigDecimal approved = claims.sumAmountByStatusIn(List.of(ClaimStatus.APPROVED));
        List<ClaimStatus> exposureStatuses = List.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW,
                ClaimStatus.MORE_INFO_REQUIRED, ClaimStatus.APPROVED);
        return new LiabilityResponse(pending, approved,
                breakdown(claims.sumAmountByClaimTypeAndStatus(exposureStatuses)),
                breakdown(claims.sumAmountByMarketAndStatus(exposureStatuses)));
    }

    /** Folds rows of [key, status, amount] into one pending/approved line per key, sorted by key. */
    private static List<LiabilityResponse.ExposureLine> breakdown(List<Object[]> rows) {
        Map<String, BigDecimal[]> byKey = new TreeMap<>();
        for (Object[] row : rows) {
            BigDecimal[] totals = byKey.computeIfAbsent(String.valueOf(row[0]),
                    key -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal amount = (BigDecimal) row[2];
            int slot = row[1] == ClaimStatus.APPROVED ? 1 : 0;
            totals[slot] = totals[slot].add(amount);
        }
        return byKey.entrySet().stream()
                .map(e -> new LiabilityResponse.ExposureLine(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OfficerPerformance> performance() {
        return users.findByRole(Role.OFFICER).stream()
                .map(officer -> {
                    long total = claims.countByAssignedOfficerId(officer.getId());
                    long active = claims.countByAssignedOfficerIdAndStatusIn(officer.getId(),
                            List.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.MORE_INFO_REQUIRED, ClaimStatus.APPROVED));
                    return new OfficerPerformance(officer.getId(), officer.getFullName(), total, active, total - active);
                }).toList();
    }

    public void requireManager(String role) {
        if (!Role.MANAGER.name().equals(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Manager access is required");
        }
    }
}
