package com.claims.controller;

import com.claims.dto.LiabilityResponse;
import com.claims.dto.OfficerPerformance;
import com.claims.dto.WorkloadResponse;
import com.claims.service.ManagerDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasRole('MANAGER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Manager dashboard", description = "Workload, liability exposure and officer performance. Managers only; all figures are computed by the database.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token"),
        @ApiResponse(responseCode = "403", description = "Managers only")
})
public class ManagerDashboardController {
    private final ManagerDashboardService dashboard;

    public ManagerDashboardController(ManagerDashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping("/workload")
    @Operation(summary = "Claims by status",
            description = "Counts per status, the total of open claims, and how many claims nobody has been assigned yet.")
    public WorkloadResponse workload() { return dashboard.workload(); }

    @GetMapping("/liability")
    @Operation(summary = "Outstanding liability exposure",
            description = "Pending exposure and approved-but-not-settled amounts, in total and broken down by claim type and by market. "
                    + "Claims carry no currency, so the totals add amounts from different markets.")
    public LiabilityResponse liability() { return dashboard.liability(); }

    @GetMapping("/performance")
    @Operation(summary = "Per-officer workload",
            description = "Assigned, active and resolved claims for every officer, including officers with none. "
                    + "The UI also uses this list as the manager's officer picker.")
    public List<OfficerPerformance> performance() { return dashboard.performance(); }
}
