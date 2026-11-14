import { signal } from '@angular/core';
import { settlementSheetModalOptions } from './settlement-sheet-options';
import { SettlementSheetComponent } from './settlement-sheet.component';

describe('settlementSheetModalOptions (DIV-70)', () => {
  it('öffnet das Ausgleichszahlungs-Sheet mit eigener Modal-ID und dem Speicher-Signal', () => {
    const saving = signal(false);
    const options = settlementSheetModalOptions({ saving });

    expect(options.component).toBe(SettlementSheetComponent);
    expect(options.componentProps).toEqual({ modalId: options.id, saving });
    expect(options.componentProps?.['saving']).toBe(saving);
  });

  // wie der Ausgabenfilter (DIV-71): inhaltshoch, ohne Breakpoints und Griff (kein Mitziehen beim Bedienen)
  it('inhaltshohes Bottom Sheet ohne Breakpoints und Griff im gemeinsamen Basis-Style', () => {
    const options = settlementSheetModalOptions({ saving: signal(false) });

    expect(options.cssClass).toEqual(['bottom-sheet', 'bottom-sheet-auto', 'settlement-sheet']);
    expect(options.breakpoints).toBeUndefined();
    expect(options.initialBreakpoint).toBeUndefined();
    expect(options.handle).toBeUndefined();
  });

  it('canDismiss sperrt das Schließen, solange gespeichert wird', async () => {
    const saving = signal(false);
    const canDismiss = settlementSheetModalOptions({ saving }).canDismiss as () => Promise<boolean>;

    expect(await canDismiss()).toBeTrue();
    saving.set(true);
    expect(await canDismiss()).toBeFalse();
    saving.set(false);
    expect(await canDismiss()).toBeTrue();
  });

  it('jedes Sheet bekommt eine eigene Modal-ID', () => {
    const erstes = settlementSheetModalOptions({ saving: signal(false) });
    const zweites = settlementSheetModalOptions({ saving: signal(false) });

    expect(erstes.id).toMatch(/^settlement-sheet-\d+$/);
    expect(zweites.id).not.toBe(erstes.id);
    expect(zweites.componentProps?.['modalId']).toBe(zweites.id);
  });
});
