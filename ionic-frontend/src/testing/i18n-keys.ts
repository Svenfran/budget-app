export type Translations = Record<string, unknown>;

// Liefert alle Blatt-Schlüssel in Punktnotation, z. B. 'global.errors.required'.
// Nur Objekte werden durchlaufen; alles andere (String, Array, Zahl, null) gilt als Blatt.
export function flattenKeys(obj: Translations, prefix = ''): string[] {
  return Object.entries(obj).reduce<string[]>((keys, [key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return keys.concat(isObject(value) ? flattenKeys(value, path) : [path]);
  }, []);
}

// Schlüssel aus reference, die in candidate fehlen – sortiert, damit die Meldung stabil ist.
export function missingKeys(reference: Translations, candidate: Translations): string[] {
  const candidateKeys = new Set(flattenKeys(candidate));
  return flattenKeys(reference)
    .filter(key => !candidateKeys.has(key))
    .sort();
}

function isObject(value: unknown): value is Translations {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
