package com.claims.controller;

import com.claims.dto.LiabilityResponse;
import com.claims.dto.OfficerPerformance;
import com.claims.dto.WorkloadResponse;
import com.claims.service.ManagerDashboardService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasRole('MANAGER')")
@SecurityRequirement(name = "bearerAuth")
public class ManagerDashboardController {
    private final ManagerDashboardService dashboard;

    public ManagerDashboardController(ManagerDashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping("/workload")
    public WorkloadResponse workload() { return dashboard.workload(); }

    @GetMapping("/liability")
    public LiabilityResponse liability() { return dashboard.liability(); }

    @GetMapping("/performance")
    public List<OfficerPerformance> performance() { return dashboard.performance(); }
}
