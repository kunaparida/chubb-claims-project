import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { STATUS_LABELS } from '../../core/claim-actions';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { CLAIM_STATUSES, Claim, ClaimStatus, PageResponse } from '../../core/models';
import { problemMessage } from '../../core/problem';
import { AmountPipe } from '../../shared/amount.pipe';
import { StatusBadge } from '../../shared/status-badge';

const PAGE_SIZE = 10;

/**
 * One list for every role — the SERVER scopes it: a claimant sees their own claims, an officer the claims assigned to them,
 * a manager all claims. Only the headings differ here.
 */
@Component({
  selector: 'app-claims-list',
  imports: [RouterLink, DatePipe, AmountPipe, StatusBadge],
  templateUrl: './claims-list.html',
})
export class ClaimsList implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly statuses = CLAIM_STATUSES;
  protected readonly labels = STATUS_LABELS;

  protected readonly isClaimant = computed(() => this.auth.user()?.role === 'CLAIMANT');
  protected readonly heading = computed(() => {
    switch (this.auth.user()?.role) {
      case 'CLAIMANT':
        return 'My claims';
      case 'OFFICER':
        return 'Assigned to me';
      default:
        return 'All claims';
    }
  });

  protected readonly status = signal<ClaimStatus | ''>('');
  protected readonly page = signal(0);
  protected readonly result = signal<PageResponse<Claim> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected setStatus(value: string): void {
    this.status.set(value as ClaimStatus | '');
    this.page.set(0);
    this.load();
  }

  protected go(delta: number): void {
    this.page.update((p) => Math.max(0, p + delta));
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listClaims({ page: this.page(), size: PAGE_SIZE, status: this.status() }).subscribe({
      next: (res) => {
        this.result.set(res);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(problemMessage(err));
      },
    });
  }

  protected selectValue(event: Event): string {
    return (event.target as HTMLSelectElement).value;
  }
}
