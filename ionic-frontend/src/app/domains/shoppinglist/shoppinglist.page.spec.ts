import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { IonicModule } from '@ionic/angular';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { AlertService } from 'src/app/service/alert.service';
import { GroupService } from 'src/app/service/group.service';
import { StorageService } from 'src/app/service/storage.service';
import { WebSocketService } from 'src/app/service/websocket.service';
import { aGroup, aLoggedInUser, aShoppingList } from 'src/testing/test-data';
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
  let session: BehaviorSubject<User | null>;
  const lists = signal<ShoppinglistDto[]>([]);

  beforeEach(() => {
    lists.set([]);
    // steuerbarer Anmeldestatus (Logout/Login), den GroupService beobachtet
    session = new BehaviorSubject<User | null>(null);
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(session);
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
        { provide: AuthService, useValue: auth },
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

  it('Logout und erneuter Login mit derselben Gruppe: die gecachte Seite lädt die Listen genau einmal neu (Regression Review F-003)', () => {
    session.next(aLoggedInUser({ id: 7 }));
    lists.set([aShoppingList({ name: 'Stand vor dem Logout' })]);

    session.next(null);
    fixture.detectChanges();
    expect(load).withContext('nach dem Logout kein Request').not.toHaveBeenCalled();

    session.next(aLoggedInUser({ id: 7 }));
    // Login: die Seitenleiste macht wieder Gruppe 1 aktiv (gespeicherte aktive Gruppe wurde beim Logout gelöscht)
    groupService.setActiveGroup(aGroup({ id: 1, name: 'WG' }));
    fixture.detectChanges();

    expect(load).toHaveBeenCalledOnceWith(jasmine.objectContaining({ id: 1 }), jasmine.any(Function));
  });

  it('Logout und Login mit derselben Gruppe ohne Change Detection dazwischen (abgehängte, gecachte View): genau ein Neuladen danach (Regression Review 3 F-001)', () => {
    session.next(aLoggedInUser({ id: 7 }));
    fixture.detectChanges();
    load.calls.reset();

    // Ionic hängt die verlassene View ab: zwischen Logout und Login läuft keine Change Detection für sie
    session.next(null);
    session.next(aLoggedInUser({ id: 7 }));
    groupService.setActiveGroup(aGroup({ id: 1, name: 'WG' }));
    fixture.detectChanges();

    expect(load).toHaveBeenCalledOnceWith(jasmine.objectContaining({ id: 1 }), jasmine.any(Function));
  });
});
