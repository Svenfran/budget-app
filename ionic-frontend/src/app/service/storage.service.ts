import { Injectable } from '@angular/core';
import { Preferences } from '@capacitor/preferences';

@Injectable({
  providedIn: 'root'
})
export class StorageService {

  constructor() { }

  async removeData(key: string) {
    await Preferences.remove({ key: key});
  }

  async setData(key: string, data: string) {
    await Preferences.set({
      key: key,
      value: JSON.stringify({ data })
    });
  }

  async getData(key: string) {
    return await Preferences.get({ key: key });
  }


  async setItem<T>(key: string, value: T): Promise<void> {
    const stringValue = typeof value === 'string' ? value : JSON.stringify(value);
    await Preferences.set({ key, value: stringValue });
    console.log(`${key}: ${stringValue}`);
  }


  async getItem<T>(key: string): Promise<T | null> {
    const { value } = await Preferences.get({ key });
    if (value === null) return null;

    try {
      return JSON.parse(value) as T; // Versuche, das gespeicherte JSON zu parsen
    } catch {
      return value as T; // Falls kein JSON, dann ist es ein normaler String
    }
  }

  async removeItem(key: string): Promise<void> {
    await Preferences.remove({ key });
  }

  async clear(): Promise<void> {
    await Preferences.clear();
  }
}
