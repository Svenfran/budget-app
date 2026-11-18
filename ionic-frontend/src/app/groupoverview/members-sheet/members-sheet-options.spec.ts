import { signal } from '@angular/core';
import { GROUP_GONE_ROLE } from '../group-sheet-roles';
import { MembersSheetComponent, MembersSheetView } from './members-sheet.component';
import { MEMBERS_SHEET_CLOSE_ROLE, membersSheetModalOptions, MembersSheetState } from './members-sheet-options';

// canDismiss direkt geprüft (Codex-Plan-Review F-002), nicht über ein gemocktes dismiss()
describe('membersSheetModalOptions (DIV-80)', () => {
  const state = (overrides: Partial<MembersSheetState> = {}): MembersSheetState => ({
    groupId: 7, groupName: 'WG', saving: signal(false), view: signal<MembersSheetView>('list'), ...overrides,
  });
  type CanDismiss = (data?: unknown, role?: string) => Promise<boolean>;
  const canDismissOf = (s: MembersSheetState) => membersSheetModalOptions(s).canDismiss as CanDismiss;

  it('öffnet das Sheet mit eigener Modal-ID, Gruppe und den geteilten Signalen', () => {
    const shared = state();

    const options = membersSheetModalOptions(shared);

    expect(options.component).toBe(MembersSheetComponent);
    expect(options.componentProps).toEqual({ modalId: options.id, groupId: 7, groupName: 'WG', saving: shared.saving, view: shared.view });
    expect(options.cssClass).toEqual(['bottom-sheet', 'bottom-sheet-auto', 'members-sheet']);
    expect(options.breakpoints).toBeUndefined();
    expect(membersSheetModalOptions(state()).id).not.toBe(options.id);
  });

  describe('canDismiss', () => {
    it('Liste ohne laufenden Request: schließt bei jeder Rolle', async () => {
      const canDismiss = canDismissOf(state());

      for (const role of [MEMBERS_SHEET_CLOSE_ROLE, 'backdrop', 'gesture', undefined]) {
        expect(await canDismiss(undefined, role)).toBeTrue();
      }
    });

    it('während eines Requests: kein Schließen, auch nicht über X', async () => {
      const canDismiss = canDismissOf(state({ saving: signal(true) }));

      for (const role of [MEMBERS_SHEET_CLOSE_ROLE, 'backdrop', 'gesture', undefined]) {
        expect(await canDismiss(undefined, role)).toBeFalse();
      }
    });

    it('Ansicht „Hinzufügen“: Hardware-Back/Backdrop führen zurück zur Liste statt zu schließen', async () => {
      const view = signal<MembersSheetView>('add');
      const canDismiss = canDismissOf(state({ view }));

      expect(await canDismiss(undefined, 'backdrop')).toBeFalse();
      expect(view()).toBe('list');
      // danach schließt ein weiterer Versuch normal
      expect(await canDismiss(undefined, 'backdrop')).toBeTrue();
    });

    it('Ansicht „Hinzufügen“: X schließt das Sheet direkt', async () => {
      const view = signal<MembersSheetView>('add');

      expect(await canDismissOf(state({ view }))(undefined, MEMBERS_SHEET_CLOSE_ROLE)).toBeTrue();
      expect(view()).toBe('add');
    });

    it('Gruppe verschwunden: schließt immer – Vorrang vor laufendem Request und Ansicht „Hinzufügen“', async () => {
      const view = signal<MembersSheetView>('add');

      expect(await canDismissOf(state({ saving: signal(true), view }))(undefined, GROUP_GONE_ROLE)).toBeTrue();
      expect(view()).toBe('add');
    });
  });
});
