package com.claims.controller;

import com.claims.dto.*;
import com.claims.entity.ClaimStatus;
import com.claims.service.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/claims")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Claims", description = "Report, queue, review, decide and settle claims. Every status change is its own command "
        + "(PUT .../approve, .../reject, ...) so the server can check it against the current status and answer 409 if it is not allowed.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token"),
        @ApiResponse(responseCode = "403", description = "The signed-in role may not do this")
})
public class ClaimController {
    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) { this.claimService = claimService; }

    @PostMapping
    @PreAuthorize("hasRole('CLAIMANT')")
    @Operation(summary = "Report an incident (submit a claim)",
            description = "Claimants only. `claimantEmail` must equal the signed-in account's email. The claim starts as SUBMITTED "
                    + "and unassigned, so it appears in the officer queue.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Claim created; the Location header points to it"),
            @ApiResponse(responseCode = "400", description = "Validation failed; the `errors` map names each field")
    })
    public ResponseEntity<ClaimResponse> create(@Valid @RequestBody CreateClaimRequest request,
                                                @AuthenticationPrincipal UserDetails user) {
        ClaimResponse created = claimService.create(request, user.getUsername());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Claimants see their own claims, officers their assigned claims, managers all. Optional status filter. */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLAIMANT','OFFICER','MANAGER')")
    @Operation(summary = "List claims (role-scoped, newest first)",
            description = "Claimant: own claims. Officer: claims assigned to them. Manager: all claims.")
    public PageResponse<ClaimResponse> list(@Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
                                            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
                                            @Parameter(description = "Only claims in this status") @RequestParam(required = false) ClaimStatus status,
                                            @AuthenticationPrincipal UserDetails user) {
        return claimService.list(user.getUsername(), false, page, size, status);
    }

    /** Officer work queue: submitted claims that nobody has picked up yet, oldest first. */
    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    @Operation(summary = "Officer work queue (oldest first)",
            description = "Claims in SUBMITTED that nobody has picked up yet. Officers and managers only.")
    public PageResponse<ClaimResponse> queue(@Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
                                             @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
                                             @AuthenticationPrincipal UserDetails user) {
        return claimService.queue(user.getUsername(), page, size);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('CLAIMANT','OFFICER','MANAGER')")
    @Operation(summary = "Audit trail of a claim",
            description = "Every status change with who made it, when, and the note. Visible to whoever may view the claim.")
    @ApiResponse(responseCode = "404", description = "No such claim, or it is not visible to you (existence is not revealed)")
    public List<ClaimHistoryResponse> history(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return claimService.history(id, user.getUsername());
    }

    /** The claimant answers an information request; the claim returns to UNDER_REVIEW. */
    @PutMapping("/{id}/provide-info")
    @PreAuthorize("hasRole('CLAIMANT')")
    @Operation(summary = "Answer an information request",
            description = "The owning claimant only, and only while the claim is MORE_INFO_REQUIRED. The claim returns to UNDER_REVIEW.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Missing or too long answer"),
            @ApiResponse(responseCode = "404", description = "Not your claim"),
            @ApiResponse(responseCode = "409", description = "No information was requested for this claim")
    })
    public ClaimResponse provideInformation(@PathVariable Long id, @Valid @RequestBody ProvideInformationRequest request,
                                            @AuthenticationPrincipal UserDetails user) {
        return claimService.provideInformation(id, request.response(), user.getUsername());
    }

    /** An officer takes an unassigned claim from the queue. */
    @PutMapping("/{id}/pickup")
    @PreAuthorize("hasRole('OFFICER')")
    @Operation(summary = "Pick up a claim from the queue",
            description = "Assigns the claim to the calling officer. The status stays SUBMITTED until the officer calls `review`.")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "No such claim"),
            @ApiResponse(responseCode = "409", description = "Already assigned to another officer")
    })
    public ClaimResponse pickUp(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return claimService.pickUp(id, user.getUsername());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLAIMANT','OFFICER','MANAGER')")
    @Operation(summary = "Get one claim",
            description = "Claimant: only their own. Officer: their own or an unassigned one. Manager: any.")
    @ApiResponse(responseCode = "404", description = "No such claim, or it is not visible to you (existence is not revealed)")
    public ClaimResponse get(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return claimService.get(id, user.getUsername());
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    @Operation(summary = "Assign a claim to an officer",
            description = "An officer may only assign to themselves (403 otherwise); a manager may assign to any officer. "
                    + "Does not change the status.")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "No such claim or officer"),
            @ApiResponse(responseCode = "409", description = "Already assigned to another officer")
    })
    public ClaimResponse assign(@PathVariable Long id, @Valid @RequestBody AssignClaimRequest request,
                                @AuthenticationPrincipal UserDetails user) {
        return claimService.assign(id, request.officerId(), user.getUsername());
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    @Operation(summary = "Start or resume review (-> UNDER_REVIEW)",
            description = "From SUBMITTED or MORE_INFO_REQUIRED. Officers: only on a claim assigned to them. Managers: any claim.")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "An officer acting on a claim that is not assigned to them"),
            @ApiResponse(responseCode = "404", description = "No such claim"),
            @ApiResponse(responseCode = "409", description = "Not allowed from the claim's current status")
    })
    public ClaimResponse review(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return claimService.review(id, user.getUsername());
    }

    @PutMapping("/{id}/request-info")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    @Operation(summary = "Ask the claimant for more information (-> MORE_INFO_REQUIRED)",
            description = "From UNDER_REVIEW. The note is shown to the claimant.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Missing or too long note"),
            @ApiResponse(responseCode = "403", description = "An officer acting on a claim that is not assigned to them"),
            @ApiResponse(responseCode = "404", description = "No such claim"),
            @ApiResponse(responseCode = "409", description = "Not allowed from the claim's current status")
    })
    public ClaimResponse requestInformation(@PathVariable Long id, @Valid @RequestBody NoteRequest request,
                                            @AuthenticationPrincipal UserDetails user) {
        return claimService.requestInformation(id, request.note(), user.getUsername());
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    @Operation(summary = "Approve a claim (-> APPROVED)", description = "From UNDER_REVIEW. The note is the decision shown to the claimant.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Missing or too long note"),
            @ApiResponse(responseCode = "403", description = "An officer acting on a claim that is not assigned to them"),
            @ApiResponse(responseCode = "404", description = "No such claim"),
            @ApiResponse(responseCode = "409", description = "Not allowed from the claim's current status")
    })
    public ClaimResponse approve(@PathVariable Long id, @Valid @RequestBody NoteRequest request,
                                 @AuthenticationPrincipal UserDetails user) {
        return claimService.decide(id, com.claims.entity.ClaimStatus.APPROVED, request.note(), user.getUsername());
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    @Operation(summary = "Reject a claim (-> REJECTED, final)", description = "From UNDER_REVIEW. The note is the reason shown to the claimant.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Missing or too long note"),
            @ApiResponse(responseCode = "403", description = "An officer acting on a claim that is not assigned to them"),
            @ApiResponse(responseCode = "404", description = "No such claim"),
            @ApiResponse(responseCode = "409", description = "Not allowed from the claim's current status")
    })
    public ClaimResponse reject(@PathVariable Long id, @Valid @RequestBody NoteRequest request,
                                @AuthenticationPrincipal UserDetails user) {
        return claimService.decide(id, com.claims.entity.ClaimStatus.REJECTED, request.note(), user.getUsername());
    }

    @PutMapping("/{id}/settle")
    @PreAuthorize("hasAnyRole('OFFICER','MANAGER')")
    @Operation(summary = "Settle an approved claim (-> SETTLED, final)",
            description = "Only from APPROVED. The note replaces the approval note in `decisionNotes`; the history keeps both.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Missing or too long note"),
            @ApiResponse(responseCode = "403", description = "An officer acting on a claim that is not assigned to them"),
            @ApiResponse(responseCode = "404", description = "No such claim"),
            @ApiResponse(responseCode = "409", description = "Not allowed from the claim's current status")
    })
    public ClaimResponse settle(@PathVariable Long id, @Valid @RequestBody NoteRequest request,
                                @AuthenticationPrincipal UserDetails user) {
        return claimService.decide(id, com.claims.entity.ClaimStatus.SETTLED, request.note(), user.getUsername());
    }
}
