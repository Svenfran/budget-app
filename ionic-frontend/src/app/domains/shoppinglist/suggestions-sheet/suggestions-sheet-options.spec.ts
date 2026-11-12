import { signal } from '@angular/core';
import { suggestionsSheetModalOptions, SuggestionsSheetProps } from './suggestions-sheet-options';
import { SuggestionsSheetComponent } from './suggestions-sheet.component';

describe('suggestionsSheetModalOptions (DIV-69)', () => {
  const props: SuggestionsSheetProps = {
    listName: 'Edeka',
    suggestions: { groupId: 7, shoppingListId: 3, defaultSuggestions: false, names: ['Milch'] },
    groupId: 7,
    listId: 3,
    inContext: () => true,
  };

  it('öffnet das Vorschlags-Sheet mit den Daten und demselben adding-Signal', () => {
    const adding = signal(false);

    const options = suggestionsSheetModalOptions(props, adding);

    expect(options.component).toBe(SuggestionsSheetComponent);
    expect(options.componentProps).toEqual({ ...props, adding, modalId: options.id });
    expect(options.componentProps?.['adding']).toBe(adding);
    expect(options.componentProps?.['inContext']).toBe(props.inContext);
  });

  // AC 10: nur die Liste scrollt, der Footer mit dem Hinzufügen-Button bleibt bei jedem Breakpoint sichtbar
  it('Bottom Sheet mit Breakpoints, Griff und festem Footer im gemeinsamen Basis-Style', () => {
    const options = suggestionsSheetModalOptions(props, signal(false));

    expect(options.breakpoints).toEqual([0, 0.75, 1]);
    expect(options.initialBreakpoint).toBe(0.75);
    expect(options.expandToScroll).toBeFalse();
    expect(options.handle).toBeTrue();
    expect(options.cssClass).toBe('bottom-sheet');
  });

  // Review F-001: die Komponente schließt gezielt ihr eigenes Modal über diese ID
  it('jedes Sheet bekommt eine eigene Modal-ID, die auch die Komponente erhält', () => {
    const erstes = suggestionsSheetModalOptions(props, signal(false));
    const zweites = suggestionsSheetModalOptions(props, signal(false));

    expect(erstes.id).toMatch(/^suggestions-sheet-\d+$/);
    expect(zweites.id).not.toBe(erstes.id);
    expect(erstes.componentProps?.['modalId']).toBe(erstes.id);
    expect(zweites.componentProps?.['modalId']).toBe(zweites.id);
  });

  it('canDismiss sperrt das Schließen, solange adding gesetzt ist', async () => {
    const adding = signal(false);
    const canDismiss = suggestionsSheetModalOptions(props, adding).canDismiss as () => Promise<boolean>;

    expect(await canDismiss()).toBeTrue();
    adding.set(true);
    expect(await canDismiss()).toBeFalse();
    adding.set(false);
    expect(await canDismiss()).toBeTrue();
  });
});
