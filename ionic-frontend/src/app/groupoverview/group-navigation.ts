import { Location } from '@angular/common';
import { Router } from '@angular/router';

/** Ziel nach der Gruppenauswahl, wenn die App keine Seite davor angezeigt hat (bisheriges Verhalten). */
export const GROUP_SELECTION_FALLBACK_URL = '/domains/tabs/overview';

/**
 * Nach der Gruppenauswahl zur aufrufenden Seite zurück – aber nur, wenn die App selbst schon eine Seite davor
 * angezeigt hat. Bei einem Direkteinstieg (Reload, URL im Browser) würde `history.back()` nichts tun oder die App
 * verlassen; dann wie bisher zur Übersicht, ohne neuen History-Eintrag (Codex-Implementierungsreview DIV-72, F-001).
 */
export function returnAfterGroupSelection(router: Router, location: Location): void {
  if (router.lastSuccessfulNavigation?.previousNavigation) {
    location.back();
  } else {
    router.navigateByUrl(GROUP_SELECTION_FALLBACK_URL, { replaceUrl: true });
  }
}
