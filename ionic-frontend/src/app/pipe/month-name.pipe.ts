import { Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

@Pipe({
  name: 'monthName',
  pure: false // wichtig, damit sich die Sprache dynamisch aktualisiert!
})
export class MonthNamePipe implements PipeTransform {
  constructor(private translate: TranslateService) {}

  transform(monthNumber: number): string {
    if (!monthNumber) return '';

    const monthNames: Record<string, string[]> = {
      de: ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'],
      en: ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'],
      es: ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre']
    };
    
    const lang = this.translate.getCurrentLang() || 'de';
    return monthNames[lang]?.[monthNumber - 1] ?? '';
  }
}
