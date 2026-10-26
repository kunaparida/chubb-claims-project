/**
 * What a claimant may attach to a claim. These limits mirror the backend (5 files per claim, 5 MB each, PDF, JPG or PNG) so a
 * mistake is explained before anything is sent; the backend checks again, including the file's real type, and has the last word.
 */
export const MAX_DOCUMENTS = 5;
export const MAX_DOCUMENT_BYTES = 5 * 1024 * 1024;
export const DOCUMENT_EXTENSIONS: readonly string[] = ['pdf', 'jpg', 'jpeg', 'png'];
/** For the file picker's `accept` attribute. */
export const DOCUMENT_ACCEPT = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png';

/** "812 B", "184 KB", "1.2 MB". */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  const mb = bytes / (1024 * 1024);
  return `${Number.isInteger(mb) ? mb : mb.toFixed(mb < 10 ? 1 : 0)} MB`;
}

function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase();
}

/**
 * Why `file` cannot be added, in words for the claimant, or null if it can.
 * `chosen` are the files already picked; `alreadyAttached` counts documents the claim already has.
 */
export function checkDocument(file: File, chosen: readonly File[], alreadyAttached = 0): string | null {
  if (chosen.length + alreadyAttached >= MAX_DOCUMENTS) {
    return `You can attach up to ${MAX_DOCUMENTS} documents to a claim.`;
  }
  if (chosen.some((c) => c.name === file.name && c.size === file.size)) {
    return `"${file.name}" is already in the list.`;
  }
  if (file.size === 0) {
    return `"${file.name}" is empty.`;
  }
  if (file.size > MAX_DOCUMENT_BYTES) {
    return `"${file.name}" is ${formatBytes(file.size)}, which is over the ${formatBytes(MAX_DOCUMENT_BYTES)} limit.`;
  }
  if (!DOCUMENT_EXTENSIONS.includes(extensionOf(file.name))) {
    return `"${file.name}" is not a PDF, JPG or PNG file.`;
  }
  return null;
}
