import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, catchError, concatMap, forkJoin, from, map, of, tap, toArray } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ACTION_LABELS, ClaimActionId, NOTE_ACTIONS, STATUS_LABELS, allowedActions, canAddDocuments } from '../../core/claim-actions';
import { claimProgress } from '../../core/claim-progress';
import { DOCUMENT_ACCEPT, MAX_DOCUMENTS, checkDocument, formatBytes } from '../../core/documents';
import { Flash } from '../../core/flash';
import { marketName } from '../../core/markets';
import { Claim, ClaimDocument, ClaimHistoryEntry, OfficerPerformance } from '../../core/models';
import { problemMessage, readBlobProblem } from '../../core/problem';
import { FileSaver } from '../../core/save-file';
import { AmountPipe } from '../../shared/amount.pipe';
import { Icon } from '../../shared/icon';
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
 * Documents: everyone who can see the claim can list and download them; only the claimant who owns it can add more.
 */
@Component({
  selector: 'app-claim-detail',
  imports: [RouterLink, DatePipe, AmountPipe, StatusBadge, Icon],
  templateUrl: './claim-detail.html',
})
export class ClaimDetail {
  /** From the route (:id) via withComponentInputBinding. */
  readonly id = input.required<string>();

  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly flash = inject(Flash);
  private readonly fileSaver = inject(FileSaver);

  protected readonly labels = ACTION_LABELS;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly marketName = marketName;
  protected readonly formatSize = formatBytes;
  protected readonly accept = DOCUMENT_ACCEPT;
  protected readonly maxDocuments = MAX_DOCUMENTS;

  protected readonly claim = signal<Claim | null>(null);
  protected readonly history = signal<ClaimHistoryEntry[]>([]);
  protected readonly officers = signal<OfficerPerformance[]>([]);
  protected readonly loadError = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly documents = signal<ClaimDocument[]>([]);
  protected readonly documentProblems = signal<string[]>([]);
  protected readonly downloadingId = signal<number | null>(null);
  protected readonly newFiles = signal<File[]>([]);
  protected readonly uploading = signal(false);

  protected readonly note = signal('');
  protected readonly response = signal('');
  protected readonly assignee = signal<number | null>(null);

  protected readonly actions = computed<ClaimActionId[]>(() => {
    const claim = this.claim();
    const user = this.auth.user();
    return claim && user ? allowedActions(claim, { id: user.id, role: user.role }) : [];
  });
  protected readonly noteActions = computed(() => this.actions().filter((a) => NOTE_ACTIONS.includes(a)));
  /** Where the claim is in its life, as a few labelled stages. Presentation only; it grants nothing. */
  protected readonly progress = computed(() => {
    const claim = this.claim();
    return claim ? claimProgress(claim.status) : [];
  });
  /** The claimant who owns an open claim can attach more documents, up to the limit. */
  protected readonly mayAddDocuments = computed(() => {
    const claim = this.claim();
    const user = this.auth.user();
    return !!claim && !!user && canAddDocuments(claim, { id: user.id, role: user.role });
  });
  protected readonly roomForDocuments = computed(() => this.documents().length + this.newFiles().length < MAX_DOCUMENTS);
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

  // ---- documents ----

  protected download(document: ClaimDocument): void {
    const claim = this.claim();
    if (!claim || this.downloadingId() !== null) {
      return;
    }
    this.downloadingId.set(document.id);
    this.documentProblems.set([]);
    this.api.downloadDocument(claim.id, document.id).subscribe({
      next: (blob) => {
        this.downloadingId.set(null);
        this.fileSaver.save(blob, document.fileName);
      },
      error: (err: unknown) => {
        this.downloadingId.set(null);
        void readBlobProblem(err).then((readable) =>
          this.documentProblems.set([problemMessage(readable, `"${document.fileName}" could not be downloaded.`)]),
        );
      },
    });
  }

  protected onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const accepted = [...this.newFiles()];
    const problems: string[] = [];
    for (const file of Array.from(input.files ?? [])) {
      const problem = checkDocument(file, accepted, this.documents().length);
      if (problem) {
        problems.push(problem);
      } else {
        accepted.push(file);
      }
    }
    this.newFiles.set(accepted);
    this.documentProblems.set(problems);
    input.value = ''; // so choosing the same file again after removing it still fires
  }

  protected removeNewFile(index: number): void {
    this.newFiles.update((files) => files.filter((_, i) => i !== index));
    this.documentProblems.set([]);
  }

  /** Uploads the chosen files one after the other; one failing does not stop the rest. Then the list is read again from the server. */
  protected uploadNewFiles(): void {
    const claim = this.claim();
    const files = this.newFiles();
    if (!claim || files.length === 0 || this.uploading()) {
      return;
    }
    this.uploading.set(true);
    this.documentProblems.set([]);
    this.message.set(null);
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
        toArray(),
        tap(() => this.uploading.set(false)),
      )
      .subscribe((results) => {
        const failures = results.filter((r): r is string => r !== null);
        const attached = files.length - failures.length;
        this.newFiles.set([]);
        this.documentProblems.set(failures);
        if (attached > 0) {
          this.message.set(`${attached} document${attached === 1 ? '' : 's'} attached.`);
        }
        this.refreshDocuments(claim.id);
      });
  }

  private refreshDocuments(claimId: number): void {
    this.api.documents(claimId).subscribe({ next: (list) => this.documents.set(list), error: () => undefined });
  }

  // ---- loading ----

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
      this.documents.set([]);
      this.newFiles.set([]);
      this.documentProblems.set([]);
    }
    this.loadError.set(null);
    forkJoin({
      claim: this.api.getClaim(claimId),
      history: this.api.history(claimId),
      // a failed action reloads the claim to show its true state; the documents are not affected, so they are not fetched again
      documents: keepMessages ? of(this.documents()) : this.api.documents(claimId).pipe(catchError(() => of([] as ClaimDocument[]))),
    }).subscribe({
      next: ({ claim, history, documents }) => {
        this.claim.set(claim);
        this.history.set(history);
        this.documents.set(documents);
        this.loadOfficersIfNeeded();
        const flash = keepMessages ? null : this.flash.take();
        if (flash?.kind === 'success') {
          this.message.set(flash.text);
        } else if (flash) {
          this.actionError.set(flash.text);
        }
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
