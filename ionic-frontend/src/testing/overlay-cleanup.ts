/**
 * Entfernt liegengebliebene Ionic-Overlays (z. B. Inline-Datums-Picker), die ein Test geöffnet hat.
 *
 * Ionic hängt ein dargestelltes Overlay außerhalb der Fixture an den `body` und setzt dort `backdrop-no-scroll`. Läuft
 * der Test mit `jasmine.clock()`, endet die Animation nie und das Overlay bleibt samt Backdrop stehen – es verdeckt
 * dann Treffertests (`elementFromPoint`) späterer Specs, je nach Zufallsreihenfolge (DIV-80). Deshalb synchron
 * entfernen statt `dismiss()`, das mit gemockter Uhr nicht fertig würde. In `afterEach` vor `jasmine.clock().uninstall()`
 * oder danach aufrufen; beides ist unkritisch.
 */
export function removeOverlays(selector: string): void {
  document.querySelectorAll(selector).forEach(el => el.remove());
  if (!document.querySelector('ion-modal, ion-popover, ion-alert, ion-action-sheet, ion-loading, ion-picker')) {
    document.body.classList.remove('backdrop-no-scroll');
  }
}
