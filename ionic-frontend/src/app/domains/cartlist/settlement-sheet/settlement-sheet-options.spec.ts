import { signal } from '@angular/core';
import { settlementSheetModalOptions, SettlementSheetState } from './settlement-sheet-options';
import { SettlementSheetComponent } from './settlement-sheet.component';

describe('settlementSheetModalOptions (DIV-70)', () => {
  let confirmDiscard: jasmine.Spy<() => Promise<boolean>>;
  const state = (overrides: Partial<SettlementSheetState> = {}): SettlementSheetState =>
    ({ saving: signal(false), dirty: signal(false), confirmDiscard, ...overrides });
  type CanDismiss = (data?: unknown, role?: string) => Promise<boolean>;
  const canDismissOf = (s: SettlementSheetState) => settlementSheetModalOptions(s).canDismiss as CanDismiss;

  beforeEach(() => confirmDiscard = jasmine.createSpy('confirmDiscard').and.resolveTo(true));

  it('öffnet das Ausgleichszahlungs-Sheet mit eigener Modal-ID und den geteilten Signalen', () => {
    const shared = state();
    const options = settlementSheetModalOptions(shared);

    expect(options.component).toBe(SettlementSheetComponent);
    expect(options.componentProps).toEqual({ modalId: options.id, saving: shared.saving, dirty: shared.dirty });
    expect(options.componentProps?.['saving']).toBe(shared.saving);
    expect(options.componentProps?.['dirty']).toBe(shared.dirty);
  });

  // wie der Ausgabenfilter (DIV-71): inhaltshoch, ohne Breakpoints und Griff (kein Mitziehen beim Bedienen)
  it('inhaltshohes Bottom Sheet ohne Breakpoints und Griff im gemeinsamen Basis-Style', () => {
    const options = settlementSheetModalOptions(state());

    expect(options.cssClass).toEqual(['bottom-sheet', 'bottom-sheet-auto', 'settlement-sheet']);
    expect(options.breakpoints).toBeUndefined();
    expect(options.initialBreakpoint).toBeUndefined();
    expect(options.handle).toBeUndefined();
  });

  it('jedes Sheet bekommt eine eigene Modal-ID', () => {
    const erstes = settlementSheetModalOptions(state());
    const zweites = settlementSheetModalOptions(state());

    expect(erstes.id).toMatch(/^settlement-sheet-\d+$/);
    expect(zweites.id).not.toBe(erstes.id);
    expect(zweites.componentProps?.['modalId']).toBe(zweites.id);
  });

  describe('canDismiss', () => {
    it('sperrt das Schließen, solange gespeichert wird – auch bei geänderten Eingaben ohne Rückfrage', async () => {
      const shared = state({ saving: signal(true), dirty: signal(true) });

      expect(await canDismissOf(shared)(undefined, 'cancel')).toBeFalse();
      expect(confirmDiscard).not.toHaveBeenCalled();
    });

    it('unveränderte Eingaben: schließt sofort ohne Rückfrage (X, Backdrop, Hardware-Back)', async () => {
      const canDismiss = canDismissOf(state());

      for (const role of ['cancel', 'backdrop', 'gesture', undefined]) {
        expect(await canDismiss(undefined, role)).toBeTrue();
      }
      expect(confirmDiscard).not.toHaveBeenCalled();
    });

    it('nach erfolgreichem Speichern (Rolle saved) schließt es trotz geänderter Eingaben ohne Rückfrage', async () => {
      expect(await canDismissOf(state({ dirty: signal(true) }))(undefined, 'saved')).toBeTrue();
      expect(confirmDiscard).not.toHaveBeenCalled();
    });

    it('geänderte Eingaben + „Verwerfen“: schließt', async () => {
      expect(await canDismissOf(state({ dirty: signal(true) }))(undefined, 'backdrop')).toBeTrue();
      expect(confirmDiscard).toHaveBeenCalledTimes(1);
    });

    it('geänderte Eingaben + „Weiter bearbeiten“: bleibt offen (E1)', async () => {
      confirmDiscard.and.resolveTo(false);

      expect(await canDismissOf(state({ dirty: signal(true) }))(undefined, 'cancel')).toBeFalse();
      expect(confirmDiscard).toHaveBeenCalledTimes(1);
    });

    it('liest die Signale beim Schließen, nicht beim Erzeugen', async () => {
      const shared = state();
      const canDismiss = canDismissOf(shared);

      shared.dirty.set(true);
      confirmDiscard.and.resolveTo(false);
      expect(await canDismiss(undefined, 'cancel')).toBeFalse();
      shared.dirty.set(false);
      expect(await canDismiss(undefined, 'cancel')).toBeTrue();
    });
  });
});
