import { HttpErrorResponse } from '@angular/common/http';
import { Problem } from './models';

/**
 * A message that is safe to show a user, from whatever an HTTP call threw. The backend writes `detail` to be shown to people;
 * for a 400 it also sends per-field `errors`. Anything unrecognised becomes a generic message — never raw JSON or a stack trace.
 */
const UNREACHABLE = 'Cannot reach the server. Check that the backend is running and try again.';

/**
 * Status 0 is a true network failure. The dev-server proxy (and any real gateway) reports a dead backend as 502/503/504 with no
 * Problem body instead — also "cannot reach the server" from the user's point of view. A backend 5xx WITH a `detail` is shown as is.
 */
const GATEWAY_ERRORS: readonly number[] = [502, 503, 504];

export function problemMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return UNREACHABLE;
    }
    const body = err.error as Problem | null;
    if (body && typeof body === 'object') {
      const fields = body.errors ? Object.values(body.errors) : [];
      if (err.status === 400 && fields.length > 0) {
        return fields.join(' · ');
      }
      if (body.detail) {
        return body.detail;
      }
    }
    if (GATEWAY_ERRORS.includes(err.status)) {
      return UNREACHABLE;
    }
    if (err.status === 403) {
      return 'You do not have permission to do that.';
    }
    if (err.status === 404) {
      return 'That could not be found.';
    }
  }
  return fallback;
}

function readText(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(blob);
  });
}

/**
 * A failed file download arrives with its body as a Blob (the request asked for a Blob), so the backend's explanation is hidden
 * inside it. This reads it back so `problemMessage` can show the real reason; if the body is not JSON the error is returned as it was.
 */
export async function readBlobProblem(err: unknown): Promise<unknown> {
  if (err instanceof HttpErrorResponse && typeof Blob !== 'undefined' && err.error instanceof Blob) {
    try {
      const body: unknown = JSON.parse(await readText(err.error));
      return new HttpErrorResponse({ error: body, headers: err.headers, status: err.status, statusText: err.statusText, url: err.url ?? undefined });
    } catch {
      return err;
    }
  }
  return err;
}

/** Per-field validation messages (HTTP 400), keyed by request field name, to show next to the inputs. */
export function fieldErrors(err: unknown): Record<string, string> {
  if (err instanceof HttpErrorResponse && err.status === 400) {
    return (err.error as Problem | null)?.errors ?? {};
  }
  return {};
}
