import { signal } from '@angular/core';
import { aGroupOverview } from 'src/testing/test-data';
import { GROUP_GONE_ROLE, GROUP_SHEET_SAVED_ROLE } from '../group-sheet-roles';
import { GroupFormSheetComponent } from './group-form-sheet.component';
import { groupFormSheetModalOptions, GroupFormSheetState } from './group-form-sheet-options';

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
    expect(options.componentProps).toEqual({ modalId: options.id, mode: 'edit', group, saving: shared.saving });
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
