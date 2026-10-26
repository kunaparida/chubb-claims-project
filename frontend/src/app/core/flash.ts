import { Injectable } from '@angular/core';

/** A message waiting for the next page to show once, e.g. what happened to the documents after a claim was submitted. */
export interface FlashMessage {
  kind: 'success' | 'warning';
  text: string;
}

@Injectable({ providedIn: 'root' })
export class Flash {
  private pending: FlashMessage | null = null;

  set(message: FlashMessage): void {
    this.pending = message;
  }

  /** Returns the waiting message, if any, and forgets it. */
  take(): FlashMessage | null {
    const message = this.pending;
    this.pending = null;
    return message;
  }
}
