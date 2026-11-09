import { Injectable } from '@angular/core';
import { FileOpener } from '@capacitor-community/file-opener';
import { Directory, Filesystem } from '@capacitor/filesystem';

/** Ergebnis von saveAndOpen: geöffnet, oder nur gespeichert (keine App zum Öffnen, z. B. für ZIP). */
export type FileExportResult = 'opened' | 'saved';

/**
 * Dünner Capacitor-Wrapper (DIV-68): speichert eine heruntergeladene Datei in „Dokumente“ und öffnet sie mit der
 * Standard-App – der bisherige Mechanismus aus CartService. Keine eigene Logik, daher ohne eigenen Spec; Services
 * und Komponenten ersetzen ihn im Test durch einen Fake.
 */
@Injectable({
  providedIn: 'root'
})
export class FileExportService {

  /** Speicherfehler werden geworfen; ein Fehler beim Öffnen nicht (die Datei liegt dann trotzdem in „Dokumente“). */
  async saveAndOpen(blob: Blob, fileName: string, mimeType: string): Promise<FileExportResult> {
    const savedFile = await Filesystem.writeFile({
      path: fileName,
      data: await this.convertBlobToBase64(blob),
      directory: Directory.Documents,
    });
    try {
      await FileOpener.open({ filePath: savedFile.uri, contentType: mimeType, openWithDefault: true });
      return 'opened';
    } catch (e) {
      console.log('Error opening file', e);
      return 'saved';
    }
  }

  private convertBlobToBase64(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = reject;
      reader.onload = () => resolve(reader.result as string);
      reader.readAsDataURL(blob);
    });
  }
}
