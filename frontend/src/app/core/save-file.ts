import { Injectable } from '@angular/core';

/** Hands a downloaded file to the browser's "save" behaviour. A service so that tests can replace the part that touches the DOM. */
@Injectable({ providedIn: 'root' })
export class FileSaver {
  save(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.rel = 'noopener';
    document.body.appendChild(link);
    link.click();
    link.remove();
    // the click has already started the download; release the temporary address afterwards
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
