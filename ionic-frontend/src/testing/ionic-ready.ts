/**
 * Ionic lädt seine Web Components im Testbrowser verzögert nach; solange ein Element hydriert, ist sein Inhalt nicht im
 * Layout. Wartet, bis alle ion-Elemente unterhalb von `root` definiert und fertig gerendert sind – vor Gesten oder
 * Layout-Messungen in Komponententests mit echtem Template.
 */
export async function ionicReady(root: HTMLElement): Promise<void> {
  const ionElements = Array.from(root.querySelectorAll('*'))
    .filter(element => element.tagName.startsWith('ION-'));
  await Promise.all([...new Set(ionElements.map(element => element.tagName.toLowerCase()))]
    .map(tag => customElements.whenDefined(tag)));
  await Promise.all(ionElements.map(element => (element as HTMLElement & { componentOnReady?: () => Promise<unknown> })
    .componentOnReady?.()));
}
