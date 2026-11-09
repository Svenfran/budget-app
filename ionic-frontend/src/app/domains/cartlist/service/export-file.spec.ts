import { exportExtension, exportFileName, exportMimeType, sanitizeFileNamePart } from './export-file';

describe('export-file', () => {
  // lokale Zeit, wie moment sie formatiert
  const now = new Date(2026, 10, 8, 15, 30, 5);
  const labels = { prefix: 'Ausgaben', allGroups: 'Alle-Gruppen' };

  describe('exportFileName', () => {
    [
      { scope: 'GROUP', format: 'XLSX', expected: 'Ausgaben_WG-Haushalt_20261108153005.xlsx' },
      { scope: 'GROUP', format: 'CSV', expected: 'Ausgaben_WG-Haushalt_20261108153005.zip' },
      { scope: 'ALL', format: 'XLSX', expected: 'Ausgaben_Alle-Gruppen_20261108153005.xlsx' },
      { scope: 'ALL', format: 'CSV', expected: 'Ausgaben_Alle-Gruppen_20261108153005.zip' },
    ].forEach(c => {
      it(`bildet für ${c.scope}/${c.format} den Namen ${c.expected}`, () => {
        const options = { scope: c.scope as 'GROUP' | 'ALL', format: c.format as 'XLSX' | 'CSV' };
        expect(exportFileName(options, 'WG Haushalt', labels, now)).toBe(c.expected);
      });
    });

    it('lässt einen leeren Gruppenteil weg', () => {
      expect(exportFileName({ scope: 'GROUP', format: 'XLSX' }, '???', labels, now))
        .toBe('Ausgaben_20261108153005.xlsx');
    });

    it('bereinigt auch übersetzte Bestandteile', () => {
      expect(exportFileName({ scope: 'ALL', format: 'CSV' }, 'egal', { prefix: 'Gastos', allGroups: 'Todos los grupos' }, now))
        .toBe('Gastos_Todos-los-grupos_20261108153005.zip');
    });
  });

  describe('Endung und MIME-Typ', () => {
    it('XLSX: .xlsx und Spreadsheet-MIME-Typ', () => {
      expect(exportExtension('XLSX')).toBe('.xlsx');
      expect(exportMimeType('XLSX')).toBe('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    });

    it('CSV: .zip und application/zip', () => {
      expect(exportExtension('CSV')).toBe('.zip');
      expect(exportMimeType('CSV')).toBe('application/zip');
    });
  });

  describe('sanitizeFileNamePart', () => {
    [
      { input: 'WG Haushalt', expected: 'WG-Haushalt' },
      { input: '  WG   Haushalt  ', expected: 'WG-Haushalt' },
      { input: 'Urlaub 2025/26', expected: 'Urlaub-202526' },
      { input: 'A:B*C?D"E<F>G|H\\I', expected: 'ABCDEFGHI' },
      { input: 'Zeile\nZwei', expected: 'ZeileZwei' },
      { input: 'Müller & Söhne 🏠', expected: 'Müller-&-Söhne-🏠' },
      { input: '.versteckt.', expected: 'versteckt' },
      { input: '???', expected: '' },
      { input: '', expected: '' },
      { input: null, expected: '' },
    ].forEach(c => {
      it(`'${c.input}' → '${c.expected}'`, () => {
        expect(sanitizeFileNamePart(c.input)).toBe(c.expected);
      });
    });

    it('kürzt auf 50 Zeichen ohne Emoji zu zerteilen', () => {
      const result = sanitizeFileNamePart('🏠'.repeat(60));

      expect(Array.from(result).length).toBe(50);
      expect(result).toBe('🏠'.repeat(50));
    });
  });
});
