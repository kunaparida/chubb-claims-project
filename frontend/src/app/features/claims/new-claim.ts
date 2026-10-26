import { Component, ElementRef, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, concatMap, from, map, of, tap, toArray } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { INCIDENT_WINDOW_DAYS, incidentWindow } from '../../core/dates';
import { DOCUMENT_ACCEPT, MAX_DOCUMENTS, MAX_DOCUMENT_BYTES, checkDocument, formatBytes } from '../../core/documents';
import { Flash } from '../../core/flash';
import { MARKETS } from '../../core/markets';
import { Claim, ClaimType } from '../../core/models';
import { generatePolicyNumber } from '../../core/policy-number';
import { fieldErrors, problemMessage } from '../../core/problem';
import { Icon } from '../../shared/icon';

const DESCRIPTION_MIN = 20;

/** Blank (empty or only spaces) counts as missing, as the backend's @NotBlank does. */
function notBlank(control: AbstractControl<string>): ValidationErrors | null {
  return (control.value ?? '').trim() === '' ? { required: true } : null;
}

/** At least `min` characters once the spaces around the text are ignored. */
function minTrimmed(min: number) {
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const length = (control.value ?? '').trim().length;
    return length > 0 && length < min ? { minlength: { requiredLength: min, actualLength: length } } : null;
  };
}

/** Mirrors the backend's @NotFutureDate. A convenience only: the server validates again and its messages are shown. */
function notInFuture(control: AbstractControl<string>): ValidationErrors | null {
  return control.value && control.value > incidentWindow().max ? { future: true } : null;
}

/** UI rule: only incidents from the last seven days can be reported. The backend has no lower bound, so only this form enforces it. */
function notTooOld(control: AbstractControl<string>): ValidationErrors | null {
  return control.value && control.value < incidentWindow().min ? { tooOld: true } : null;
}

/** Mirrors @ValidClaimAmount: greater than zero, at most two decimal places. */
function money(control: AbstractControl<number | null>): ValidationErrors | null {
  const v = control.value;
  if (v === null || v === undefined) {
    return null;
  }
  return v > 0 && Math.round(v * 100) / 100 === v ? null : { amount: true };
}

type Field = 'claimantName' | 'policyNumber' | 'claimType' | 'market' | 'incidentDate' | 'description' | 'amountClaimed';

/** The order the fields appear on the page, so the summary and the focus go top to bottom. */
const FIELD_ORDER: readonly Field[] = ['claimantName', 'claimType', 'market', 'policyNumber', 'incidentDate', 'description', 'amountClaimed'];

export interface Issue {
  /** The id of the field's element. */
  id: Field;
  message: string;
}

/**
 * Report an incident. The claimant's name and email come from the session (the backend rejects any other email); the market is
 * picked from a list; the incident date is limited to the last seven days; and the policy number is generated, not typed.
 * Supporting documents are optional: they are uploaded one by one right after the claim is created.
 * Every missing or invalid field is explained, beside the field and in a summary at the bottom.
 */
@Component({
  selector: 'app-new-claim',
  imports: [ReactiveFormsModule, RouterLink, Icon],
  templateUrl: './new-claim.html',
})
export class NewClaim {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly flash = inject(Flash);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly markets = MARKETS;
  protected readonly windowDays = INCIDENT_WINDOW_DAYS;
  protected readonly minDate = incidentWindow().min;
  protected readonly maxDate = incidentWindow().max;
  protected readonly email = this.auth.user()?.email ?? '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});
  /** True once the claimant has pressed "Submit claim": from then on every problem is shown, not just those on fields they touched. */
  protected readonly submitted = signal(false);

  protected readonly accept = DOCUMENT_ACCEPT;
  protected readonly maxDocuments = MAX_DOCUMENTS;
  protected readonly maxDocumentSize = formatBytes(MAX_DOCUMENT_BYTES);
  protected readonly formatSize = formatBytes;
  protected readonly files = signal<File[]>([]);
  protected readonly fileProblems = signal<string[]>([]);
  protected readonly dragging = signal(false);
  protected readonly progress = signal<{ done: number; total: number } | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    claimantName: [this.auth.user()?.fullName ?? '', [Validators.required, notBlank, Validators.maxLength(120)]],
    policyNumber: [generatePolicyNumber(), [Validators.required, Validators.maxLength(64)]],
    claimType: ['MOTOR' as ClaimType, Validators.required],
    market: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    incidentDate: ['', [Validators.required, notInFuture, notTooOld]],
    description: ['', [Validators.required, notBlank, minTrimmed(DESCRIPTION_MIN), Validators.maxLength(4000)]],
    amountClaimed: [null as number | null, [Validators.required, money]],
  });

  protected problem(field: string): string | undefined {
    return this.serverErrors()[field];
  }

  /** What is wrong with a field, in words for the claimant, or null if it is fine. */
  protected errorFor(field: Field): string | null {
    const errors = this.form.controls[field].errors;
    if (!errors) {
      return null;
    }
    switch (field) {
      case 'claimantName':
        return errors['maxlength'] ? 'Your name must be 120 characters or fewer.' : 'Enter your name.';
      case 'policyNumber':
        return errors['maxlength']
          ? 'The policy number must be 64 characters or fewer.'
          : 'A policy number is required. Use the button beside the field to generate one.';
      case 'claimType':
        return 'Choose the type of claim.';
      case 'market':
        return 'Select the market the claim relates to.';
      case 'incidentDate':
        if (errors['future']) {
          return 'The incident date cannot be in the future.';
        }
        if (errors['tooOld']) {
          return `Only incidents from the last ${this.windowDays} days can be reported (from ${this.minDate}).`;
        }
        return 'Enter the date of the incident.';
      case 'description':
        if (errors['maxlength']) {
          return 'The description must be 4000 characters or fewer.';
        }
        if (errors['minlength']) {
          return `Describe what happened in at least ${DESCRIPTION_MIN} characters (you have written ${errors['minlength'].actualLength}).`;
        }
        return 'Describe what happened.';
      case 'amountClaimed':
        return errors['amount'] ? 'Enter an amount greater than zero with at most two decimals.' : 'Enter the amount you are claiming.';
    }
  }

  /** Show a field's problem once the claimant has been to the field, or has tried to submit. */
  protected showError(field: Field): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || this.submitted());
  }

  /** Every field that needs attention, top to bottom. */
  protected issues(): Issue[] {
    const found: Issue[] = [];
    for (const field of FIELD_ORDER) {
      const message = this.errorFor(field);
      if (message) {
        found.push({ id: field, message });
      }
    }
    return found;
  }

  protected focusField(event: Event | null, id: string): void {
    event?.preventDefault();
    const element = this.host.nativeElement.querySelector<HTMLElement>('#' + id);
    element?.focus();
    element?.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
  }

  protected regeneratePolicyNumber(): void {
    this.form.controls.policyNumber.setValue(generatePolicyNumber());
  }

  // ---- optional documents ----

  protected addFiles(list: ArrayLike<File> | null | undefined): void {
    if (!list || list.length === 0) {
      return;
    }
    const accepted = [...this.files()];
    const problems: string[] = [];
    for (const file of Array.from(list)) {
      const problem = checkDocument(file, accepted);
      if (problem) {
        problems.push(problem);
      } else {
        accepted.push(file);
      }
    }
    this.files.set(accepted);
    this.fileProblems.set(problems);
  }

  protected onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.addFiles(input.files);
    input.value = ''; // so choosing the same file again after removing it still fires
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.addFiles(event.dataTransfer?.files);
  }

  protected removeFile(index: number): void {
    this.files.update((files) => files.filter((_, i) => i !== index));
    this.fileProblems.set([]);
  }

  // ---- submit ----

  protected submit(): void {
    if (this.busy()) {
      return;
    }
    this.submitted.set(true);
    this.error.set(null);
    this.serverErrors.set({});
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      const first = this.issues()[0];
      if (first) {
        this.focusField(null, first.id);
      }
      return;
    }
    this.busy.set(true);
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
        next: (claim) => this.attachDocuments(claim),
        error: (err: unknown) => {
          this.busy.set(false);
          const fields = fieldErrors(err);
          this.serverErrors.set(fields);
          // field problems show beside the inputs; anything else (e.g. a 403 email mismatch) shows as a banner
          this.error.set(Object.keys(fields).length ? 'Please correct the highlighted fields.' : problemMessage(err));
        },
      });
  }

  /**
   * The claim exists now. Upload each chosen document in turn; a document that fails does not stop the others and does not undo the
   * claim, it is reported on the claim page, where more documents can be added.
   */
  private attachDocuments(claim: Claim): void {
    const files = this.files();
    if (files.length === 0) {
      void this.router.navigate(['/claims', claim.id]);
      return;
    }
    this.progress.set({ done: 0, total: files.length });
    from(files)
      .pipe(
        concatMap((file) =>
          this.api.uploadDocument(claim.id, file).pipe(
            map(() => null),
            catchError((err: unknown) => {
              const message = problemMessage(err, `"${file.name}" could not be uploaded.`);
              return of(message.includes(file.name) ? message : `"${file.name}": ${message}`);
            }),
          ),
        ),
        tap(() => this.progress.update((p) => (p ? { ...p, done: p.done + 1 } : p))),
        toArray(),
      )
      .subscribe((results) => {
        const failures = results.filter((r): r is string => r !== null);
        if (failures.length === 0) {
          const n = files.length;
          this.flash.set({ kind: 'success', text: `Claim submitted with ${n} document${n === 1 ? '' : 's'}.` });
        } else {
          this.flash.set({
            kind: 'warning',
            text: `Your claim was submitted, but ${failures.length} of ${files.length} documents could not be attached. ${failures.join(' ')} You can add them in the Documents section below.`,
          });
        }
        void this.router.navigate(['/claims', claim.id]);
      });
  }
}
