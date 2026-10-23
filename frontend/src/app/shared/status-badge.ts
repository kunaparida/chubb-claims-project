import { Component, computed, input } from '@angular/core';
import { STATUS_LABELS } from '../core/claim-actions';
import { ClaimStatus } from '../core/models';

@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [class]="'badge status-' + status().toLowerCase()">{{ label() }}</span>`,
})
export class StatusBadge {
  readonly status = input.required<ClaimStatus>();
  protected readonly label = computed(() => STATUS_LABELS[this.status()] ?? this.status());
}
