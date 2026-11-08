import moment from 'moment';
import { ExportFormat, ExportOptions } from '../model/export-options';

/** Übersetzte Bestandteile des Dateinamens, z. B. `Ausgaben` / `Alle-Gruppen`. */
export interface ExportFileNameLabels {
  prefix: string;
  allGroups: string;
}

const MAX_NAME_PART = 50;

/**
 * Dateiname des Exports (DIV-68): `<Präfix>_<Gruppe>_<YYYYMMDDHHmmss>.xlsx` bzw. `<Präfix>_<Alle-Gruppen>_….zip`.
 * Ergibt der Gruppenname nach dem Bereinigen nichts, entfällt dieser Teil.
 */
export function exportFileName(options: ExportOptions, groupName: string, labels: ExportFileNameLabels, now: Date): string {
  const scopePart = options.scope === 'ALL' ? labels.allGroups : groupName;
  const parts = [sanitizeFileNamePart(labels.prefix), sanitizeFileNamePart(scopePart), moment(now).format('YYYYMMDDHHmmss')];
  return parts.filter(part => part.length > 0).join('_') + exportExtension(options.format);
}

export function exportExtension(format: ExportFormat): string {
  return format === 'XLSX' ? '.xlsx' : '.zip';
}

export function exportMimeType(format: ExportFormat): string {
  return format === 'XLSX'
    ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    : 'application/zip';
}

/**
 * Teil eines Dateinamens für das Android-Dateisystem: Leerzeichen → `-`, in Dateinamen verbotene Zeichen
 * (`\ / : * ? " < > |`) und Steuerzeichen entfernt, auf 50 Zeichen gekürzt, keine Punkte/Bindestriche am Rand.
 */
export function sanitizeFileNamePart(value: string | null | undefined): string {
  // eslint-disable-next-line no-control-regex
  const cleaned = (value ?? '').replace(/[\\/:*?"<>|\u0000-\u001f\u007f]/g, '').trim().replace(/\s+/g, '-');
  return Array.from(cleaned).slice(0, MAX_NAME_PART).join('').replace(/^[.-]+|[.-]+$/g, '');
}
