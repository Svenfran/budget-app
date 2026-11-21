import { signal } from '@angular/core';
import { ModalController } from '@ionic/angular';
import { aGroupOverview } from 'src/testing/test-data';
import { GROUP_GONE_ROLE, GROUP_SHEET_SAVED_ROLE } from '../group-sheet-roles';
import { GroupFormSheetComponent } from './group-form-sheet.component';
import { groupFormSheetModalOptions, GroupFormSheetState, openGroupFormSheet } from './group-form-sheet-options';

// canDismiss direkt geprüft (Codex-Plan-Review F-002), nicht über ein gemocktes dismiss()
describe('groupFormSheetModalOptions (DIV-80)', () => {
  const state = (overrides: Partial<GroupFormSheetState> = {}): GroupFormSheetState =>
    ({ mode: 'create', saving: signal(false), ...overrides });
  type CanDismiss = (data?: unknown, role?: string) => Promise<boolean>;
  const canDismissOf = (s: GroupFormSheetState) => groupFormSheetModalOptions(s).canDismiss as CanDismiss;

  it('öffnet das Sheet mit eigener Modal-ID, Modus, Gruppe und geteiltem saving-Signal', () => {
    const group = aGroupOverview();
    const shared = state({ mode: 'edit', group });

    const options = groupFormSheetModalOptions(shared);

    expect(options.component).toBe(GroupFormSheetComponent);
    expect(options.componentProps).toEqual({ modalId: options.id, mode: 'edit', group, saving: shared.saving, callingPage: undefined });
    expect(options.componentProps?.['saving']).toBe(shared.saving);
  });

  it('inhaltshohes Bottom Sheet ohne Breakpoints im gemeinsamen Basis-Style; eigene ID je Sheet', () => {
    const erstes = groupFormSheetModalOptions(state());
    const zweites = groupFormSheetModalOptions(state());

    expect(erstes.cssClass).toEqual(['bottom-sheet', 'bottom-sheet-auto', 'group-form-sheet']);
    expect(erstes.breakpoints).toBeUndefined();
    expect(erstes.id).toMatch(/^group-form-sheet-\d+$/);
    expect(zweites.id).not.toBe(erstes.id);
  });

  // DIV-77: gemeinsamer Öffner für Gruppenübersicht, Seitenmenü und „Keine Gruppe“
  describe('openGroupFormSheet', () => {
    let setFocus: jasmine.Spy;
    let create: jasmine.Spy;
    let present: jasmine.Spy;

    beforeEach(() => {
      setFocus = jasmine.createSpy('setFocus').and.resolveTo();
      present = jasmine.createSpy('present').and.resolveTo();
      create = jasmine.createSpy('create').and.resolveTo({ present, querySelector: () => ({ setFocus }) });
    });
    const modalCtrl = () => ({ create } as unknown as ModalController);

    it('Anlegen: öffnet das Sheet mit der aufrufenden Seite und setzt den Fokus ins Namensfeld', async () => {
      await openGroupFormSheet(modalCtrl(), state({ callingPage: '/no-group' }));

      expect(create.calls.mostRecent().args[0].componentProps).toEqual(jasmine.objectContaining({ mode: 'create', callingPage: '/no-group' }));
      expect(present).toHaveBeenCalledTimes(1);
      expect(setFocus).toHaveBeenCalledTimes(1);
    });

    it('Bearbeiten: kein Fokus (Tastatur bleibt zu)', async () => {
      await openGroupFormSheet(modalCtrl(), state({ mode: 'edit', group: aGroupOverview() }));

      expect(present).toHaveBeenCalledTimes(1);
      expect(setFocus).not.toHaveBeenCalled();
    });
  });

  describe('canDismiss', () => {
    it('schließt ohne laufendes Speichern bei jeder Rolle (X, Abbrechen, Backdrop, Hardware-Back, gespeichert)', async () => {
      const canDismiss = canDismissOf(state());

      for (const role of ['cancel', 'backdrop', 'gesture', undefined, GROUP_SHEET_SAVED_ROLE]) {
        expect(await canDismiss(undefined, role)).toBeTrue();
      }
    });

    it('sperrt das Schließen, solange gespeichert wird', async () => {
      const canDismiss = canDismissOf(state({ saving: signal(true) }));

      for (const role of ['cancel', 'backdrop', 'gesture', undefined]) {
        expect(await canDismiss(undefined, role)).toBeFalse();
      }
    });

    it('Gruppe verschwunden: schließt auch während des Speicherns (Vorrang vor der Sperre)', async () => {
      expect(await canDismissOf(state({ saving: signal(true) }))(undefined, GROUP_GONE_ROLE)).toBeTrue();
    });
  });
});
