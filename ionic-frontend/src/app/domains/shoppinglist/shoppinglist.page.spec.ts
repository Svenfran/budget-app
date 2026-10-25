import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { IonicModule } from '@ionic/angular';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from 'src/app/auth/auth.service';
import { AlertService } from 'src/app/service/alert.service';
import { GroupService } from 'src/app/service/group.service';
import { StorageService } from 'src/app/service/storage.service';
import { WebSocketService } from 'src/app/service/websocket.service';
import { aGroup, aShoppingList } from 'src/testing/test-data';
import { alertServiceSpy, authServiceStub, provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { ShoppinglistDto } from './model/shoppinglist-dto';
import { ShoppinglistService } from './service/shoppinglist.service';
import { ShoppinglistPage } from './shoppinglist.page';

// DIV-36: Die Seite lädt nur bei einem echten Gruppenwechsel neu; eine Umbenennung (Live-Event oder Resync)
// aktualisiert nur die angezeigte Überschrift. Overview und Cartlist haben dieselbe Zwei-Effect-Struktur.
describe('ShoppinglistPage – Gruppenwechsel und Umbenennung', () => {
  let fixture: ComponentFixture<ShoppinglistPage>;
  let page: ShoppinglistPage;
  let groupService: GroupService;
  let load: jasmine.Spy;
  const lists = signal<ShoppinglistDto[]>([]);

  beforeEach(() => {
    lists.set([]);
    load = jasmine.createSpy('getShoppingListsWithItems')
      .and.callFake((_group: unknown, onComplete?: () => void) => onComplete?.());
    TestBed.configureTestingModule({
      declarations: [ShoppinglistPage],
      imports: [IonicModule.forRoot(), FormsModule, TranslateModule],
      providers: [
        ...provideServiceTesting(),
        { provide: ShoppinglistService, useValue: { getShoppingLists: () => lists, getShoppingListsWithItems: load } },
        { provide: WebSocketService, useValue: jasmine.createSpyObj('WebSocketService', ['subscribe', 'unsubscribeOwner']) },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: authServiceStub(null) },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    groupService = TestBed.inject(GroupService);
    groupService.setActiveGroup(aGroup({ id: 1, name: 'WG' }));
    fixture = TestBed.createComponent(ShoppinglistPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
    load.calls.reset();
  });

  function headerText(): string {
    return (fixture.nativeElement as HTMLElement).querySelector('.group-name')?.textContent ?? '';
  }

  it('Umbenennung der aktiven Gruppe: neuer Name in der Überschrift, Listen weder geleert noch neu geladen', () => {
    const loaded = [aShoppingList()];
    lists.set(loaded);

    groupService.setActiveGroup(aGroup({ id: 1, name: 'Neue WG' }));
    fixture.detectChanges();

    expect(page.activeGroup.name).toBe('Neue WG');
    expect(headerText()).toContain('Neue WG');
    expect(load).not.toHaveBeenCalled();
    expect(lists()).toBe(loaded);
    expect(page.isLoading).toBeFalse();
  });

  it('Wechsel auf eine andere Gruppe: Listen werden geleert und genau einmal für die neue Gruppe geladen', () => {
    lists.set([aShoppingList()]);

    groupService.setActiveGroup(aGroup({ id: 2, name: 'Urlaub' }));
    fixture.detectChanges();

    expect(load).toHaveBeenCalledOnceWith(jasmine.objectContaining({ id: 2, name: 'Urlaub' }), jasmine.any(Function));
    expect(lists()).toEqual([]);
    expect(page.activeGroup.name).toBe('Urlaub');
    expect(headerText()).toContain('Urlaub');
  });
});
