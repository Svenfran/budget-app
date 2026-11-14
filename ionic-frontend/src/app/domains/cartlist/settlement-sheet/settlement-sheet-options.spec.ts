import { signal } from '@angular/core';
import { settlementSheetModalOptions, SettlementSheetState } from './settlement-sheet-options';
import { SettlementSheetComponent } from './settlement-sheet.component';

describe('settlementSheetModalOptions (DIV-70)', () => {
  let confirmDiscard: jasmine.Spy<() => Promise<boolean>>;
  const state = (overrides: Partial<SettlementSheetState> = {}): SettlementSheetState => ({
    saving: signal(false), dirty: signal(false), confirmDiscard,
    dismiss: jasmine.createSpy('dismiss').and.resolveTo(true), ...overrides,
  });
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

    const settle = () => new Promise(resolve => setTimeout(resolve));

    it('geänderte Eingaben + „Verwerfen“: hält zunächst fest, schließt dann das eigene Sheet; weitere Prüfungen lassen durch', async () => {
      const shared = state({ dirty: signal(true) });
      const canDismiss = canDismissOf(shared);

      expect(await canDismiss(undefined, 'backdrop')).toBeFalse();
      await settle();

      expect(confirmDiscard).toHaveBeenCalledTimes(1);
      expect(shared.dismiss).toHaveBeenCalledTimes(1);
      expect(await canDismiss(undefined, 'cancel')).toBeTrue();
      expect(confirmDiscard).toHaveBeenCalledTimes(1);
    });

    it('„Verwerfen“ schließt über die eigene Modal-ID', async () => {
      const shared = state({ dirty: signal(true) });
      const options = settlementSheetModalOptions(shared);

      await (options.canDismiss as CanDismiss)(undefined, 'cancel');
      await settle();

      expect(shared.dismiss).toHaveBeenCalledOnceWith(options.id!);
    });

    // Codex-Implementierungsreview 2, F-001: Die Verwerfen-Freigabe gilt nur, wenn das Sheet wirklich geschlossen wurde.
    [
      { name: 'liefert false', dismiss: () => Promise.resolve(false) },
      { name: 'wird zurückgewiesen', dismiss: () => Promise.reject(new Error('overlay does not exist')) },
    ].forEach(c => {
      it(`„Verwerfen“, aber Schließen ${c.name}: der nächste Schließversuch fragt wieder`, async () => {
        spyOn(console, 'error');
        const shared = state({ dirty: signal(true), dismiss: jasmine.createSpy('dismiss').and.callFake(c.dismiss) });
        const canDismiss = canDismissOf(shared);

        expect(await canDismiss(undefined, 'cancel')).toBeFalse();
        await settle();
        expect(shared.dismiss).toHaveBeenCalledTimes(1);

        confirmDiscard.and.resolveTo(false);
        expect(await canDismiss(undefined, 'cancel')).toBeFalse();
        await settle();
        expect(confirmDiscard).toHaveBeenCalledTimes(2);
      });
    });

    it('geänderte Eingaben + „Weiter bearbeiten“: bleibt offen; ein späterer Versuch fragt erneut (E1)', async () => {
      confirmDiscard.and.resolveTo(false);
      const shared = state({ dirty: signal(true) });
      const canDismiss = canDismissOf(shared);

      expect(await canDismiss(undefined, 'cancel')).toBeFalse();
      await settle();
      expect(shared.dismiss).not.toHaveBeenCalled();

      expect(await canDismiss(undefined, 'cancel')).toBeFalse();
      await settle();
      expect(confirmDiscard).toHaveBeenCalledTimes(2);
      expect(shared.dismiss).not.toHaveBeenCalled();
    });

    // Codex-Implementierungsreview F-001: Ionic serialisiert dismiss() und prüft canDismiss erst nach dem vorherigen
    // Aufruf. Ein Doppel-Tipp (X, Backdrop, Hardware-Back) durfte keine zweite Rückfrage nachreichen.
    describe('mehrere Schließversuche hintereinander (wie von Ionic serialisiert)', () => {
      let answer: (discard: boolean) => void;
      const closeTwice = (canDismiss: CanDismiss) =>
        canDismiss(undefined, 'cancel').then(() => canDismiss(undefined, 'backdrop'));

      beforeEach(() => confirmDiscard.and.callFake(() => new Promise<boolean>(resolve => answer = resolve)));

      it('„Weiter bearbeiten“: genau eine Rückfrage, Sheet bleibt offen', async () => {
        const shared = state({ dirty: signal(true) });
        const closing = closeTwice(canDismissOf(shared));

        await Promise.resolve();
        answer(false);
        await closing;
        await new Promise(resolve => setTimeout(resolve));

        expect(confirmDiscard).toHaveBeenCalledTimes(1);
        expect(shared.dismiss).not.toHaveBeenCalled();
      });

      it('„Verwerfen“: genau eine Rückfrage und genau ein Schließen', async () => {
        const shared = state({ dirty: signal(true) });
        const closing = closeTwice(canDismissOf(shared));

        await Promise.resolve();
        answer(true);
        await closing;
        await new Promise(resolve => setTimeout(resolve));

        expect(confirmDiscard).toHaveBeenCalledTimes(1);
        expect(shared.dismiss).toHaveBeenCalledTimes(1);
      });
    });

    it('liest die Signale beim Schließen, nicht beim Erzeugen', async () => {
      const shared = state();
      const canDismiss = canDismissOf(shared);

      shared.dirty.set(true);
      confirmDiscard.and.resolveTo(false);
      expect(await canDismiss(undefined, 'cancel')).toBeFalse();
      await settle();
      shared.dirty.set(false);
      expect(await canDismiss(undefined, 'cancel')).toBeTrue();
    });
  });
});
