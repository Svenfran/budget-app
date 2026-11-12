import { CartFilter } from '../model/cart-filter';
import { filterSheetModalOptions } from './filter-sheet-options';
import { FilterSheetComponent } from './filter-sheet.component';

describe('filterSheetModalOptions (DIV-71)', () => {
  const filter: CartFilter = { title: 'Edeka', category: ['Lebensmittel'] };

  it('öffnet das Filter-Sheet mit dem übergebenen Filter und der eigenen Modal-ID', () => {
    const options = filterSheetModalOptions(filter);

    expect(options.component).toBe(FilterSheetComponent);
    expect(options.componentProps).toEqual({ cartFilter: filter, modalId: options.id });
    expect(options.componentProps?.['cartFilter']).toBe(filter);
  });

  // FA1/Leitplanke: nur die Felder scrollen, der Footer mit den Aktionen bleibt bei jedem Breakpoint sichtbar
  it('Bottom Sheet mit Breakpoints, Griff und festem Footer im gemeinsamen Basis-Style', () => {
    const options = filterSheetModalOptions(filter);

    expect(options.breakpoints).toEqual([0, 0.75, 1]);
    expect(options.initialBreakpoint).toBe(0.75);
    expect(options.expandToScroll).toBeFalse();
    expect(options.handle).toBeTrue();
    expect(options.cssClass).toEqual(['bottom-sheet', 'filter-sheet']);
  });

  it('ohne Schließ-Sperre: Wischen, Backdrop und Hardware-Back schließen jederzeit (ohne Übernahme)', () => {
    expect(filterSheetModalOptions(filter).canDismiss).toBeUndefined();
  });

  it('jedes Sheet bekommt eine eigene Modal-ID', () => {
    const erstes = filterSheetModalOptions(filter);
    const zweites = filterSheetModalOptions(filter);

    expect(erstes.id).toMatch(/^filter-sheet-\d+$/);
    expect(zweites.id).not.toBe(erstes.id);
    expect(zweites.componentProps?.['modalId']).toBe(zweites.id);
  });
});
