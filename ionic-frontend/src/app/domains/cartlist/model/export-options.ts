/** Umfang des Ausgabenexports (DIV-68): aktive Gruppe oder alle Gruppen des Nutzers (serverseitig bestimmt). */
export type ExportScope = 'GROUP' | 'ALL';

/** Dateiformat: Excel-Arbeitsmappe oder ZIP mit CSV-Dateien. Werte = Backend-Enum `ExportFormat`. */
export type ExportFormat = 'XLSX' | 'CSV';

export interface ExportOptions {
  scope: ExportScope;
  format: ExportFormat;
}
