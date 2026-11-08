import { signal } from '@angular/core';
import { aGroup } from 'src/testing/test-data';
import { exportDialogModalOptions } from './export-dialog-options';
import { ExportDialogComponent } from './export-dialog.component';

describe('exportDialogModalOptions', () => {

  it('öffnet den Export-Dialog unten angedockt (export-sheet) mit Gruppe und demselben exporting-Signal', () => {
    const group = aGroup({ name: 'WG' });
    const exporting = signal(false);

    const options = exportDialogModalOptions(group, exporting);

    expect(options.component).toBe(ExportDialogComponent);
    expect(options.componentProps?.['group']).toBe(group);
    expect(options.componentProps?.['exporting']).toBe(exporting);
    expect(options.cssClass).toBe('export-sheet');
    expect(options.breakpoints).toBeUndefined();
  });

  it('canDismiss sperrt das Schließen, solange exporting gesetzt ist', async () => {
    const exporting = signal(false);
    const canDismiss = exportDialogModalOptions(aGroup(), exporting).canDismiss as () => Promise<boolean>;

    expect(await canDismiss()).toBeTrue();
    exporting.set(true);
    expect(await canDismiss()).toBeFalse();
    exporting.set(false);
    expect(await canDismiss()).toBeTrue();
  });
});
