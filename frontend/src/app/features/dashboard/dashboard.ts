import { Component, OnInit, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Liability, OfficerPerformance, Workload } from '../../core/models';
import { problemMessage } from '../../core/problem';
import { AmountPipe } from '../../shared/amount.pipe';

/**
 * The manager's picture: where claims are, what the team is carrying, and the outstanding liability.
 * The backend has no currency field and sums the liability totals across markets, so the per-market lines are shown first and
 * the grand total is labelled as unconverted rather than presented as one currency.
 */
@Component({
  selector: 'app-dashboard',
  imports: [AmountPipe],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly workload = signal<Workload | null>(null);
  protected readonly liability = signal<Liability | null>(null);
  protected readonly performance = signal<OfficerPerformance[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      workload: this.api.workload(),
      liability: this.api.liability(),
      performance: this.api.performance(),
    }).subscribe({
      next: (r) => {
        this.workload.set(r.workload);
        this.liability.set(r.liability);
        this.performance.set(r.performance);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(problemMessage(err));
      },
    });
  }
}
