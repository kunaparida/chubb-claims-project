import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ClaimType } from '../../core/models';
import { fieldErrors, problemMessage } from '../../core/problem';

const todayIso = (): string => new Date().toISOString().slice(0, 10);

/** Mirrors the backend's @NotFutureDate. A convenience only — the server validates again and its messages are shown. */
function notInFuture(control: AbstractControl<string>): ValidationErrors | null {
  return control.value && control.value > todayIso() ? { future: true } : null;
}

/** Mirrors @ValidClaimAmount: greater than zero, at most two decimal places. */
function money(control: AbstractControl<number | null>): ValidationErrors | null {
  const v = control.value;
  if (v === null || v === undefined) {
    return null;
  }
  return v > 0 && Math.round(v * 100) / 100 === v ? null : { amount: true };
}

/** Report an incident. The claimant's name and email come from the session — the backend rejects any other email. */
@Component({
  selector: 'app-new-claim',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './new-claim.html',
})
export class NewClaim {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly today = todayIso();
  protected readonly email = this.auth.user()?.email ?? '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected readonly form = this.fb.nonNullable.group({
    claimantName: [this.auth.user()?.fullName ?? '', [Validators.required, Validators.maxLength(120)]],
    policyNumber: ['', [Validators.required, Validators.maxLength(64)]],
    claimType: ['MOTOR' as ClaimType, Validators.required],
    market: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    incidentDate: ['', [Validators.required, notInFuture]],
    description: ['', [Validators.required, Validators.minLength(20), Validators.maxLength(4000)]],
    amountClaimed: [null as number | null, [Validators.required, money]],
  });

  protected problem(field: string): string | undefined {
    return this.serverErrors()[field];
  }

  protected submit(): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.serverErrors.set({});
    const v = this.form.getRawValue();
    this.api
      .createClaim({
        policyNumber: v.policyNumber.trim(),
        claimantName: v.claimantName.trim(),
        claimantEmail: this.email,
        incidentDate: v.incidentDate,
        description: v.description.trim(),
        amountClaimed: v.amountClaimed as number,
        claimType: v.claimType,
        market: v.market.trim().toUpperCase(),
      })
      .subscribe({
        next: (claim) => void this.router.navigate(['/claims', claim.id]),
        error: (err: unknown) => {
          this.busy.set(false);
          const fields = fieldErrors(err);
          this.serverErrors.set(fields);
          // field problems show beside the inputs; anything else (e.g. a 403 email mismatch) shows as a banner
          this.error.set(Object.keys(fields).length ? 'Please correct the highlighted fields.' : problemMessage(err));
        },
      });
  }
}
