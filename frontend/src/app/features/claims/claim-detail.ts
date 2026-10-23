import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ACTION_LABELS, ClaimActionId, NOTE_ACTIONS, STATUS_LABELS, allowedActions } from '../../core/claim-actions';
import { Claim, ClaimHistoryEntry, OfficerPerformance } from '../../core/models';
import { problemMessage } from '../../core/problem';
import { AmountPipe } from '../../shared/amount.pipe';
import { StatusBadge } from '../../shared/status-badge';

const SUCCESS: Record<ClaimActionId, string> = {
  PICKUP: 'You picked up this claim. Start the review when you are ready.',
  ASSIGN: 'Claim assigned.',
  REVIEW: 'Review started.',
  REQUEST_INFO: 'Your question was sent to the claimant.',
  PROVIDE_INFO: 'Thank you — your information was sent.',
  APPROVE: 'Claim approved.',
  REJECT: 'Claim rejected.',
  SETTLE: 'Claim marked as settled.',
};

/**
 * One page for every role. What the person can DO comes only from the central action table (`allowedActions`); the server then
 * has the last word, so every failure (403/404/409) is shown and the claim is reloaded to show its true current state.
 */
@Component({
  selector: 'app-claim-detail',
  imports: [RouterLink, DatePipe, AmountPipe, StatusBadge],
  templateUrl: './claim-detail.html',
})
export class ClaimDetail {
  /** From the route (:id) via withComponentInputBinding. */
  readonly id = input.required<string>();

  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly labels = ACTION_LABELS;
  protected readonly statusLabels = STATUS_LABELS;

  protected readonly claim = signal<Claim | null>(null);
  protected readonly history = signal<ClaimHistoryEntry[]>([]);
  protected readonly officers = signal<OfficerPerformance[]>([]);
  protected readonly loadError = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly note = signal('');
  protected readonly response = signal('');
  protected readonly assignee = signal<number | null>(null);

  protected readonly actions = computed<ClaimActionId[]>(() => {
    const claim = this.claim();
    const user = this.auth.user();
    return claim && user ? allowedActions(claim, { id: user.id, role: user.role }) : [];
  });
  protected readonly noteActions = computed(() => this.actions().filter((a) => NOTE_ACTIONS.includes(a)));
  protected readonly backLink = computed(() => {
    const role = this.auth.user()?.role;
    return role === 'CLAIMANT' ? '/claims' : role === 'OFFICER' ? '/queue' : '/dashboard';
  });

  constructor() {
    // (re)load whenever :id changes — the same instance is reused when navigating claim to claim
    effect(() => {
      const claimId = Number(this.id());
      untracked(() => this.load(claimId));
    });
  }

  protected can(action: ClaimActionId): boolean {
    return this.actions().includes(action);
  }

  protected text(event: Event): string {
    return (event.target as HTMLTextAreaElement | HTMLInputElement).value;
  }

  protected selectOfficer(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.assignee.set(value ? Number(value) : null);
  }

  protected canSubmit(action: ClaimActionId): boolean {
    if (this.busy()) {
      return false;
    }
    switch (action) {
      case 'PROVIDE_INFO':
        return this.response().trim().length > 0;
      case 'ASSIGN':
        return this.assignee() !== null;
      case 'REQUEST_INFO':
      case 'APPROVE':
      case 'REJECT':
      case 'SETTLE':
        return this.note().trim().length > 0; // the backend requires a non-blank note for these
      default:
        return true;
    }
  }

  protected perform(action: ClaimActionId): void {
    const claim = this.claim();
    if (!claim || !this.canSubmit(action)) {
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.message.set(null);
    this.call(action, claim.id).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.claim.set(updated);
        this.message.set(SUCCESS[action]);
        this.note.set('');
        this.response.set('');
        this.assignee.set(null);
        this.loadOfficersIfNeeded();
        this.api.history(updated.id).subscribe({ next: (h) => this.history.set(h), error: () => undefined });
      },
      error: (err: unknown) => {
        // 403/404/409 usually mean the claim changed under us: show the server's reason, then show the truth
        this.busy.set(false);
        this.actionError.set(problemMessage(err));
        this.load(claim.id, true);
      },
    });
  }

  private call(action: ClaimActionId, id: number): Observable<Claim> {
    const note = this.note().trim();
    switch (action) {
      case 'PICKUP':
        return this.api.pickup(id);
      case 'ASSIGN':
        return this.api.assign(id, this.assignee() as number);
      case 'REVIEW':
        return this.api.review(id);
      case 'REQUEST_INFO':
        return this.api.requestInfo(id, note);
      case 'PROVIDE_INFO':
        return this.api.provideInfo(id, this.response().trim());
      case 'APPROVE':
        return this.api.approve(id, note);
      case 'REJECT':
        return this.api.reject(id, note);
      case 'SETTLE':
        return this.api.settle(id, note);
    }
  }

  private load(claimId: number, keepMessages = false): void {
    if (!keepMessages) {
      this.claim.set(null);
      this.message.set(null);
      this.actionError.set(null);
    }
    this.loadError.set(null);
    forkJoin({ claim: this.api.getClaim(claimId), history: this.api.history(claimId) }).subscribe({
      next: ({ claim, history }) => {
        this.claim.set(claim);
        this.history.set(history);
        this.loadOfficersIfNeeded();
      },
      error: (err: unknown) => this.loadError.set(problemMessage(err, 'This claim could not be loaded.')),
    });
  }

  /** There is no officers endpoint: the manager's picker uses the dashboard's performance list (it lists every officer). */
  private loadOfficersIfNeeded(): void {
    if (this.can('ASSIGN') && this.officers().length === 0) {
      this.api.performance().subscribe({ next: (list) => this.officers.set(list), error: () => undefined });
    }
  }
}
