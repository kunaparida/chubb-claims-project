import { DOCUMENT_ACCEPT, MAX_DOCUMENTS, MAX_DOCUMENT_BYTES, checkDocument, formatBytes } from './documents';

const file = (name: string, size = 1000) => new File([new Uint8Array(size)], name);

describe('documents', () => {
  describe('limits', () => {
    it('mirror the backend: five files of five megabytes', () => {
      expect(MAX_DOCUMENTS).toBe(5);
      expect(MAX_DOCUMENT_BYTES).toBe(5 * 1024 * 1024);
    });

    it('offer only PDF, JPG and PNG in the file picker', () => {
      expect(DOCUMENT_ACCEPT).toContain('.pdf');
      expect(DOCUMENT_ACCEPT).toContain('.jpg');
      expect(DOCUMENT_ACCEPT).toContain('.png');
      expect(DOCUMENT_ACCEPT).not.toContain('.docx');
    });
  });

  describe('formatBytes', () => {
    it.each([
      [0, '0 B'],
      [812, '812 B'],
      [1024, '1 KB'],
      [188_416, '184 KB'],
      [1_572_864, '1.5 MB'],
      [5_242_880, '5 MB'],
      [12 * 1024 * 1024, '12 MB'],
    ])('%d bytes is %s', (bytes, text) => {
      expect(formatBytes(bytes)).toBe(text);
    });
  });

  describe('checkDocument', () => {
    it.each(['estimate.pdf', 'PHOTO.JPG', 'photo.jpeg', 'scan.PNG', 'my.report.final.pdf'])('accepts %s', (name) => {
      expect(checkDocument(file(name), [])).toBeNull();
    });

    it('accepts a file of exactly the size limit', () => {
      expect(checkDocument(file('exact.pdf', MAX_DOCUMENT_BYTES), [])).toBeNull();
    });

    it('explains a file over the limit, with its size', () => {
      expect(checkDocument(file('big.pdf', MAX_DOCUMENT_BYTES + 1), [])).toBe('"big.pdf" is 5.0 MB, which is over the 5 MB limit.');
      expect(checkDocument(file('huge.pdf', 12 * 1024 * 1024), [])).toBe('"huge.pdf" is 12 MB, which is over the 5 MB limit.');
    });

    it.each(['notes.txt', 'letter.docx', 'archive.zip', 'no-extension', 'photo.pdf.exe'])('refuses %s as the wrong kind of file', (name) => {
      expect(checkDocument(file(name), [])).toBe(`"${name}" is not a PDF, JPG or PNG file.`);
    });

    it('refuses an empty file', () => {
      expect(checkDocument(file('empty.pdf', 0), [])).toBe('"empty.pdf" is empty.');
    });

    it('refuses the same file twice, but not a different file with the same name', () => {
      const first = file('estimate.pdf', 1000);
      expect(checkDocument(file('estimate.pdf', 1000), [first])).toBe('"estimate.pdf" is already in the list.');
      expect(checkDocument(file('estimate.pdf', 2000), [first])).toBeNull();
    });

    it('stops at five documents, counting the ones the claim already has', () => {
      const four = ['a', 'b', 'c', 'd'].map((n) => file(`${n}.pdf`));
      expect(checkDocument(file('e.pdf'), four)).toBeNull();
      const five = [...four, file('e.pdf')];
      expect(checkDocument(file('f.pdf'), five)).toBe('You can attach up to 5 documents to a claim.');
      expect(checkDocument(file('x.pdf'), [], 5)).toBe('You can attach up to 5 documents to a claim.');
      expect(checkDocument(file('x.pdf'), [file('y.pdf')], 3)).toBeNull();
      expect(checkDocument(file('x.pdf'), [file('y.pdf'), file('z.pdf')], 3)).toBe('You can attach up to 5 documents to a claim.');
    });
  });
});
