import { Injectable, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private _currentLang = signal<string>('de');
  readonly currentLang = this._currentLang.asReadonly();

  constructor(private translate: TranslateService) {
    this.translate.addLangs(['de', 'en', 'es']);
    const savedLang = localStorage.getItem('appLang');
    const defaultLang = savedLang || 'de';
    this.setLanguage(defaultLang);
  }

  setLanguage(lang: string) {
    this._currentLang.set(lang);
    this.translate.use(lang);
    localStorage.setItem('appLang', lang);
  }
}
