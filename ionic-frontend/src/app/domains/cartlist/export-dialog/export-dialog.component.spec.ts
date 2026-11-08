import { signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IonicModule, ModalController } from '@ionic/angular';
import { TranslateModule } from '@ngx-translate/core';
import { aGroup } from 'src/testing/test-data';
import { provideServiceTesting } from 'src/testing/test-providers';
import { ExportOptions } from '../model/export-options';
import { CartService } from '../service/cart.service';
import { ExportDialogComponent } from './export-dialog.component';

describe('ExportDialogComponent (DIV-68)', () => {
  let fixture: ComponentFixture<ExportDialogComponent>;
  let component: ExportDialogComponent;
  let exporting: WritableSignal<boolean>;
  let exportCarts: jasmine.Spy<(options: ExportOptions, group: unknown) => Promise<boolean>>;
  let modalCtrl: jasmine.SpyObj<ModalController>;
  let exportingWhenDismissed: boolean | undefined;
  const group = aGroup({ id: 7, name: 'WG Haushalt' });

  const el = (): HTMLElement => fixture.nativeElement;
  const radioGroups = () => el().querySelectorAll('ion-radio-group');
  const change = (index: number, value: string) => {
    radioGroups()[index].dispatchEvent(new CustomEvent('ionChange', { detail: { value } }));
    fixture.detectChanges();
  };
  const exportButton = () => el().querySelector('ion-button.btn-export') as HTMLIonButtonElement;
  const cancelButton = () => el().querySelector('ion-button.btn-cancel') as HTMLIonButtonElement;

  beforeEach(() => {
    exportCarts = jasmine.createSpy('exportCarts');
    modalCtrl = jasmine.createSpyObj<ModalController>('ModalController', ['dismiss']);
    exportingWhenDismissed = undefined;
    modalCtrl.dismiss.and.callFake(async () => {
      exportingWhenDismissed = exporting();
      return true;
    });
    TestBed.configureTestingModule({
      declarations: [ExportDialogComponent],
      imports: [IonicModule.forRoot(), TranslateModule],
      providers: [
        ...provideServiceTesting(),
        { provide: CartService, useValue: { exportCarts } },
        { provide: ModalController, useValue: modalCtrl },
      ],
    });
    fixture = TestBed.createComponent(ExportDialogComponent);
    component = fixture.componentInstance;
    exporting = signal(false);
    component.group = group;
    component.exporting = exporting;
    fixture.detectChanges();
  });

  it('wählt standardmäßig „Aktuelle Gruppe“ und „Excel (.xlsx)“ und zeigt den Gruppennamen (AC1)', () => {
    expect(component.scope()).toBe('GROUP');
    expect(component.format()).toBe('XLSX');
    expect((radioGroups()[0] as HTMLIonRadioGroupElement).value).toBe('GROUP');
    expect((radioGroups()[1] as HTMLIonRadioGroupElement).value).toBe('XLSX');
    expect(el().textContent).toContain('WG Haushalt');
  });

  // Regression: ion-radio blendet seinen Label-Bereich aus, wenn es beim eigenen Rendern noch keinen Text enthält
  // (übersetzte Texte kommen erst danach) – „Alle Gruppen“ war bis zum Anklicken leer. Texte stehen deshalb im
  // ion-label daneben, die Radios selbst bleiben leer und sind per aria-label beschriftet.
  it('zeigt die Optionstexte in ion-labels neben den Radios, nicht im Radio selbst', () => {
    const items = Array.from(el().querySelectorAll('ion-radio-group ion-item'));

    expect(items.length).toBe(4);
    items.forEach(item => {
      const radio = item.querySelector('ion-radio')!;
      expect(radio.textContent?.trim()).toBe('');
      expect(radio.getAttribute('aria-label')).toMatch(/^domains\.spendings\.export\./);
      expect(item.querySelector('ion-label .option-title')?.textContent?.trim()).toBeTruthy();
      expect(item.querySelector('ion-label .option-description')?.textContent?.trim()).toBeTruthy();
    });
    // Text über die Spans prüfen: ion-label patcht textContent (Stencil-Slots), sobald es definiert ist
    expect(items[1].querySelector('ion-label .option-description')?.textContent)
      .toBe('domains.spendings.export.scope_all_description');
  });

  // Regression: ion-item leitet nur Klicks auf seinen Innenabstand an das Radio weiter, nicht Klicks auf das ion-label.
  it('wählt eine Option per Klick auf ihren Text aus', () => {
    const labels = el().querySelectorAll('ion-radio-group ion-item ion-label');

    (labels[1] as HTMLElement).click();
    (labels[3] as HTMLElement).click();
    fixture.detectChanges();

    expect(component.scope()).toBe('ALL');
    expect(component.format()).toBe('CSV');
    expect((radioGroups()[0] as HTMLIonRadioGroupElement).value).toBe('ALL');
    expect((radioGroups()[1] as HTMLIonRadioGroupElement).value).toBe('CSV');
  });

  it('ändert die Auswahl während des Exports nicht', () => {
    exporting.set(true);

    component.onScopeChange('ALL');
    component.onFormatChange('CSV');

    expect(component.scope()).toBe('GROUP');
    expect(component.format()).toBe('XLSX');
  });

  it('wechselt die Formatbeschreibungen mit dem Umfang', () => {
    expect(el().textContent).toContain('domains.spendings.export.format_xlsx_desc_group');
    expect(el().textContent).toContain('domains.spendings.export.format_csv_desc_group');

    change(0, 'ALL');

    expect(el().textContent).toContain('domains.spendings.export.format_xlsx_desc_all');
    expect(el().textContent).toContain('domains.spendings.export.format_csv_desc_all');
    expect(el().textContent).not.toContain('domains.spendings.export.format_xlsx_desc_group');
  });

  it('exportiert mit dem gewählten Umfang und Format', () => {
    exportCarts.and.returnValue(new Promise(() => undefined));
    change(0, 'ALL');
    change(1, 'CSV');

    exportButton().click();

    expect(exportCarts).toHaveBeenCalledOnceWith({ scope: 'ALL', format: 'CSV' }, group);
  });

  it('sperrt Auswahl und Buttons während des Exports', () => {
    exportCarts.and.returnValue(new Promise(() => undefined));

    component.export();
    fixture.detectChanges();

    expect(exporting()).toBeTrue();
    expect(exportButton().disabled).toBeTrue();
    expect(cancelButton().disabled).toBeTrue();
    el().querySelectorAll('ion-radio').forEach(radio => expect((radio as HTMLIonRadioElement).disabled).toBeTrue());
    expect(el().querySelector('ion-button.btn-export ion-spinner')).not.toBeNull();
  });

  it('setzt nach Erfolg zuerst exporting zurück und schließt dann den Dialog', async () => {
    exportCarts.and.resolveTo(true);

    await component.export();

    expect(modalCtrl.dismiss).toHaveBeenCalledTimes(1);
    expect(exportingWhenDismissed).toBeFalse();
    expect(exporting()).toBeFalse();
  });

  it('bleibt bei einem Fehler offen und gibt die Auswahl wieder frei', async () => {
    exportCarts.and.resolveTo(false);

    await component.export();
    fixture.detectChanges();

    expect(modalCtrl.dismiss).not.toHaveBeenCalled();
    expect(exporting()).toBeFalse();
    expect(exportButton().disabled).toBeFalse();
  });

  it('startet keinen zweiten Export, solange einer läuft', () => {
    exportCarts.and.returnValue(new Promise(() => undefined));

    component.export();
    component.export();

    expect(exportCarts).toHaveBeenCalledTimes(1);
  });

  it('Abbrechen schließt den Dialog ohne Export', () => {
    cancelButton().click();

    expect(modalCtrl.dismiss).toHaveBeenCalledTimes(1);
    expect(exportCarts).not.toHaveBeenCalled();
  });
});
