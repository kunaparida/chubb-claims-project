import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Claim, PageResponse } from '../../core/models';
import { problemMessage } from '../../core/problem';
import { AmountPipe } from '../../shared/amount.pipe';

const PAGE_SIZE = 10;

/**
 * The officer work queue: unassigned SUBMITTED claims, oldest first (the server orders it). Officers can pick a claim up here;
 * managers see the same queue but assign from the claim page (the backend only lets OFFICERS use "pickup").
 */
@Component({
  selector: 'app-queue',
  imports: [RouterLink, DatePipe, AmountPipe],
  templateUrl: './queue.html',
})
export class Queue implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly isOfficer = computed(() => this.auth.user()?.role === 'OFFICER');
  protected readonly page = signal(0);
  protected readonly result = signal<PageResponse<Claim> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<{ id: number; claimNumber: string } | null>(null);
  protected readonly busyId = signal<number | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected go(delta: number): void {
    this.page.update((p) => Math.max(0, p + delta));
    this.load();
  }

  /** `keepError`: reloading right after a failed action must not wipe the message explaining that failure. */
  protected load(keepError = false): void {
    this.loading.set(true);
    if (!keepError) {
      this.error.set(null);
    }
    this.api.queue(this.page(), PAGE_SIZE).subscribe({
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

  protected pickUp(claim: Claim): void {
    this.busyId.set(claim.id);
    this.error.set(null);
    this.notice.set(null);
    this.api.pickup(claim.id).subscribe({
      next: (picked) => {
        this.busyId.set(null);
        this.notice.set({ id: picked.id, claimNumber: picked.claimNumber });
        this.load();
      },
      error: (err: unknown) => {
        // typically a 409: another officer was first — say why, then show the queue as it really is now
        this.busyId.set(null);
        this.error.set(problemMessage(err));
        this.load(true);
      },
    });
  }
}
