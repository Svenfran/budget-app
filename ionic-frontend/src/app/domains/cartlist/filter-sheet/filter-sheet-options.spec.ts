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

  // Ruhiges Sheet beim Bedienen: inhaltshoch wie der Export-Dialog, ohne Breakpoints und Griff (kein Mitziehen)
  it('inhaltshohes Bottom Sheet ohne Breakpoints und Griff im gemeinsamen Basis-Style', () => {
    const options = filterSheetModalOptions(filter);

    expect(options.cssClass).toEqual(['bottom-sheet', 'bottom-sheet-auto', 'filter-sheet']);
    expect(options.breakpoints).toBeUndefined();
    expect(options.initialBreakpoint).toBeUndefined();
    expect(options.handle).toBeUndefined();
  });

  it('ohne Schließ-Sperre: Backdrop und Hardware-Back schließen jederzeit (ohne Übernahme)', () => {
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
