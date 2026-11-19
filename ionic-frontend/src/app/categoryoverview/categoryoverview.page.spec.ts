import { signal, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { IonicModule } from '@ionic/angular';
import { CartService } from '../domains/cartlist/service/cart.service';
import { CategoryDto } from '../model/category-dto';
import { CategoryService } from '../service/category.service';
import { GroupService } from '../service/group.service';
import { aCategory, aGroup } from 'src/testing/test-data';
import { provideServiceTesting } from 'src/testing/test-providers';
import { CategoryoverviewPage } from './categoryoverview.page';

// DIV-74: Die Settlement-Kategorie wird automatisch verwaltet und ist für Einkäufe nicht wählbar; die Übersicht zeigt
// nur die verwaltbaren Kategorien. Template ausgeblendet – geprüft wird die Liste, die das Template rendert.
describe('CategoryoverviewPage – Settlement-Kategorie (DIV-74)', () => {
  const lebensmittel = aCategory({ name: 'Lebensmittel', groupId: 3 });
  const ausgleich = aCategory({ name: 'Ausgleichszahlung', groupId: 3, settlement: true });
  const namensgleich = aCategory({ name: 'Ausgleichszahlung ', groupId: 3 });
  let categories: WritableSignal<CategoryDto[]>;
  let page: CategoryoverviewPage;

  beforeEach(() => {
    categories = signal<CategoryDto[]>([]);
    TestBed.configureTestingModule({
      declarations: [CategoryoverviewPage],
      imports: [IonicModule.forRoot()],
      providers: [
        ...provideServiceTesting(),
        { provide: CategoryService, useValue: { categories, getCategoriesByGroup: jasmine.createSpy('load') } },
        { provide: CartService, useValue: { triggerUpdate: jasmine.createSpy('triggerUpdate') } },
      ],
    });
    TestBed.overrideTemplate(CategoryoverviewPage, '');
    TestBed.inject(GroupService).setActiveGroup(aGroup({ id: 3, name: 'WG' }));
    const fixture = TestBed.createComponent(CategoryoverviewPage);
    fixture.detectChanges();
    page = fixture.componentInstance;
  });

  it('zeigt die Settlement-Kategorie nicht, erkannt am Kennzeichen; eine gleichnamige normale Kategorie bleibt', () => {
    categories.set([lebensmittel, ausgleich, namensgleich]);

    expect(page.categories()).toEqual([lebensmittel, namensgleich]);
  });

  it('folgt Änderungen der geladenen Kategorien', () => {
    categories.set([ausgleich]);
    expect(page.categories()).toEqual([]);

    categories.set([ausgleich, lebensmittel]);
    expect(page.categories()).toEqual([lebensmittel]);
  });
});
