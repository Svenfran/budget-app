import de from 'src/assets/i18n/de.json';
import en from 'src/assets/i18n/en.json';
import es from 'src/assets/i18n/es.json';
import { flattenKeys, missingKeys, Translations } from 'src/testing/i18n-keys';

// de ist die Default-Sprache und damit die Referenz; geprüft wird in beide Richtungen.
describe('i18n-Konsistenz', () => {
  it('de.json enthält Übersetzungen', () => {
    // Schutz gegen leeren/fehlerhaften Import: sonst wären alle Vergleiche trivial grün.
    expect(flattenKeys(de).length).toBeGreaterThan(0);
  });

  const others: [string, Translations][] = [['en', en], ['es', es]];
  others.forEach(([lang, translations]) => {
    describe(`${lang}.json gegen de.json`, () => {
      it('enthält alle Schlüssel aus de.json', () => {
        expect(missingKeys(de, translations)).withContext(`Schlüssel fehlen in ${lang}.json`).toEqual([]);
      });

      it('enthält keine Schlüssel, die in de.json fehlen', () => {
        expect(missingKeys(translations, de)).withContext(`Schlüssel nur in ${lang}.json, nicht in de.json`).toEqual([]);
      });
    });
  });
});
