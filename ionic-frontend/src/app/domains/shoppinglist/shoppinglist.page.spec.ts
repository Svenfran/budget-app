import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { HttpTestingController } from '@angular/common/http/testing';
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
import { environment } from 'src/environments/environment';
import { aGroup, aLoggedInUser, aShoppingItem, aShoppingList, REFERENCE_DATE } from 'src/testing/test-data';
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
      imports: [IonicModule.forRoot(), FormsModule, TranslateModule, DragDropModule],
      providers: [
        ...provideServiceTesting(),
        { provide: ShoppinglistService, useValue: { getShoppingLists: () => lists, getShoppingListsWithItems: load, releaseUpdates: () => undefined } },
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

// DIV-1: Drag & Drop. Mit echtem ShoppinglistService und gemocktem HTTP; die CDK-Events werden als Objekte an die
// Handler übergeben (ohne echte Geste), WebSocket-Handler werden über den Subscribe-Spy direkt aufgerufen.
describe('ShoppinglistPage – Drag & Drop', () => {
  let fixture: ComponentFixture<ShoppinglistPage>;
  let page: ShoppinglistPage;
  let service: ShoppinglistService;
  let groupService: GroupService;
  let http: HttpTestingController;
  let session: BehaviorSubject<User | null>;
  let handlers: Record<string, (data: unknown) => void>;
  const api = `${environment.apiBaseUrl}/api/groups`;
  const loadUrl = (groupId: number) =>
    `${api}/shopping-lists-with-items/${groupId}`;
  const wg = aGroup({ id: 1, name: 'WG' });
  const urlaub = aGroup({ id: 2, name: 'Urlaub' });

  const drop = (data: number, currentIndex: number) =>
    ({ item: { data }, currentIndex }) as unknown as CdkDragDrop<unknown, unknown, number>;
  const listIds = () => service.getShoppingLists()().map(list => list.id);
  const listA = () => service.getShoppingLists()().find(list => list.id === 1)!;
  const itemIdsOfA = () => listA().shoppingItems.map(item => item.id);
  const event = (topic: string, data: unknown) => handlers[`/user/notification/${topic}`](data);
  /** Listen A (1, Einträge 11-13), B (2), C (3) mit Revisionen wie aus dem GET. */
  const loadedLists = (): ShoppinglistDto[] => [1, 2, 3].map(id => ({
    id, name: `Liste ${id}`, listOrderRevision: 5, itemOrderRevision: 2,
    shoppingItems: id === 1 ? [11, 12, 13].map(itemId => aShoppingItem({ id: itemId })) : [],
  }));

  beforeEach(() => {
    spyOn(console, 'error');
    handlers = {};
    session = new BehaviorSubject<User | null>(aLoggedInUser({ id: 7 }));
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(session);
    const webSocket = jasmine.createSpyObj<WebSocketService>('WebSocketService', ['subscribe', 'unsubscribeOwner']);
    webSocket.subscribe.and.callFake(((topic: string, handler: (data: unknown) => void) => {
      handlers[topic] = handler;
    }) as never);
    TestBed.configureTestingModule({
      declarations: [ShoppinglistPage],
      imports: [IonicModule.forRoot(), FormsModule, TranslateModule, DragDropModule],
      providers: [
        ...provideServiceTesting(),
        { provide: WebSocketService, useValue: webSocket },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: auth },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    groupService = TestBed.inject(GroupService);
    service = TestBed.inject(ShoppinglistService);
    http = TestBed.inject(HttpTestingController);
    groupService.setActiveGroup(wg);
    fixture = TestBed.createComponent(ShoppinglistPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne(loadUrl(1)).flush(loadedLists());
  });

  afterEach(() => http.verify());

  describe('Drop', () => {
    it('Drop einer Liste: moveShoppingList mit der gezogenen ID und dem Zielindex, PUT mit der neuen Reihenfolge', () => {
      const move = spyOn(service, 'moveShoppingList').and.callThrough();

      page.onListDropped(drop(3, 0));

      expect(move).toHaveBeenCalledOnceWith(jasmine.objectContaining({ id: 1 }), 3, 0);
      const req = http.expectOne(`${api}/shopping-list/reorder`);
      expect(req.request.body.shoppingListIds).toEqual([3, 1, 2]);
      req.flush({ groupId: 1, revision: 6, shoppingListIds: [3, 1, 2] });
      expect(listIds()).toEqual([3, 1, 2]);
    });

    it('Drop eines Eintrags: moveShoppingItem für seine Liste', () => {
      const move = spyOn(service, 'moveShoppingItem').and.callThrough();

      page.onItemDropped(listA(), drop(13, 0));

      expect(move).toHaveBeenCalledOnceWith(jasmine.objectContaining({ id: 1 }), 1, 13, 0);
      http.expectOne(`${api}/shopping-list/reorder-items`).flush({ groupId: 1, shoppingListId: 1, revision: 3, shoppingItemIds: [13, 11, 12] });
      expect(itemIdsOfA()).toEqual([13, 11, 12]);
    });

    it('Drop einer inzwischen gelöschten Liste bzw. eines gelöschten Eintrags: kein Aufruf, kein Request', () => {
      const moveList = spyOn(service, 'moveShoppingList');
      const moveItem = spyOn(service, 'moveShoppingItem');
      const staleA = listA();
      event('delete-list', { id: 3, name: 'Liste 3', groupId: 1 });
      event('delete-item', { id: 13, name: 'x', completed: false, shoppingListId: 1, groupId: 1 });

      page.onListDropped(drop(3, 0));
      page.onItemDropped(staleA, drop(13, 0));

      expect(moveList).not.toHaveBeenCalled();
      expect(moveItem).not.toHaveBeenCalled();
    });
  });

  describe('Abhaken', () => {
    it('ein fehlgeschlagenes Abhaken zeigt keinen Toast (Abhaken ohne Netz, eigene Offline-Story)', () => {
      const alerts = TestBed.inject(AlertService) as jasmine.SpyObj<AlertService>;

      page.markAsDone(listA(), listA().shoppingItems[0]);
      http.expectOne(`${api}/shopping-list/update-item`).flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(alerts.showToast).not.toHaveBeenCalled();
    });
  });

  describe('Klick nach dem Drag', () => {
    beforeEach(() => {
      jasmine.clock().install();
      jasmine.clock().mockDate(REFERENCE_DATE);
    });

    afterEach(() => jasmine.clock().uninstall());

    it('der Click direkt nach dem Drag-Ende wird ignoriert (kein Auf-/Zuklappen, kein Abhaken), der folgende wirkt', () => {
      const item = listA().shoppingItems[0];

      page.onDragEnded();
      page.toggleList(listA());
      page.markAsDone(listA(), item);

      expect(page.toggleLists[1]).toBeFalsy();
      http.expectNone(`${api}/shopping-list/update-item`);

      jasmine.clock().tick(500);
      page.toggleList(listA());
      page.markAsDone(listA(), item);

      expect(page.toggleLists[1]).toBeTrue();
      http.expectOne(`${api}/shopping-list/update-item`).flush({ ...item, completed: true, shoppingListId: 1, groupId: 1 });
    });
  });

  describe('Puffer für Updates während des Drags (WebSocket, GET, REST, PUT-Antworten)', () => {
    it('ein Update während des Drags wird erst nach dem Drop angewandt', () => {
      page.onDragStarted();
      event('update-list', { id: 2, name: 'Neu', groupId: 1 });

      expect(service.getShoppingLists()()[1].name).toBe('Liste 2');

      page.onDragEnded();
      page.onListDropped(drop(2, 1));

      expect(service.getShoppingLists()()[1].name).toBe('Neu');
    });

    it('echte CDK-Folge (Listen): ended, dann dropped – der Move rechnet gegen [A,B,C], danach wird die gepufferte Löschung von A angewandt', async () => {
      page.onDragStarted();
      event('delete-list', { id: 1, name: 'Liste 1', groupId: 1 });

      page.onDragEnded();
      page.onListDropped(drop(3, 1));
      await Promise.resolve();

      const req = http.expectOne(`${api}/shopping-list/reorder`);
      expect(req.request.body.shoppingListIds).toEqual([1, 3, 2]);
      expect(listIds()).toEqual([3, 2]);
      req.flush({ groupId: 1, revision: 6, shoppingListIds: [1, 3, 2] });
      expect(listIds()).toEqual([3, 2]);
    });

    it('echte CDK-Folge (Einträge): der Move rechnet gegen [11,12,13], danach wird die gepufferte Löschung von 11 angewandt', async () => {
      page.onDragStarted();
      event('delete-item', { id: 11, name: 'x', completed: false, shoppingListId: 1, groupId: 1 });

      page.onDragEnded();
      page.onItemDropped(listA(), drop(13, 1));
      await Promise.resolve();

      const req = http.expectOne(`${api}/shopping-list/reorder-items`);
      expect(req.request.body.shoppingItemIds).toEqual([11, 13, 12]);
      expect(itemIdsOfA()).toEqual([13, 12]);
      req.flush({ groupId: 1, shoppingListId: 1, revision: 3, shoppingItemIds: [11, 13, 12] });
      expect(itemIdsOfA()).toEqual([13, 12]);
    });

    it('Drag ohne Drop: der Puffer wird nach dem Drag-Ende per Microtask geleert', async () => {
      page.onDragStarted();
      event('update-list', { id: 2, name: 'Neu', groupId: 1 });

      page.onDragEnded();
      expect(service.getShoppingLists()()[1].name).toBe('Liste 2');
      await Promise.resolve();

      expect(service.getShoppingLists()()[1].name).toBe('Neu');
    });

    it('Gruppenwechsel bei gefülltem Puffer: der Puffer wird verworfen, die neue Gruppe bleibt unverändert', async () => {
      page.onDragStarted();
      event('add-list', { id: 9, name: 'Neu', groupId: 1 });

      groupService.setActiveGroup(urlaub);
      fixture.detectChanges();
      http.expectOne(loadUrl(2)).flush([aShoppingList({ id: 20 })]);
      page.onDragEnded();
      await Promise.resolve();

      expect(listIds()).toEqual([20]);
    });

    it('Logout/Login mit derselben Gruppe bei gefülltem Puffer: der Puffer wird verworfen', async () => {
      page.onDragStarted();
      event('add-list', { id: 9, name: 'Neu', groupId: 1 });

      session.next(null);
      session.next(aLoggedInUser({ id: 7 }));
      groupService.setActiveGroup(wg);
      fixture.detectChanges();
      http.expectOne(loadUrl(1)).flush(loadedLists());
      page.onDragEnded();
      await Promise.resolve();

      expect(listIds()).toEqual([1, 2, 3]);
    });

    it('ein Eintrag mit veralteter Kontext-Generation wird beim Leeren nicht ausgeführt (Wechsel ohne Neuladen der Seite)', async () => {
      page.onDragStarted();
      event('update-list', { id: 2, name: 'Neu', groupId: 1 });
      groupService.setActiveGroup(urlaub);

      page.onDragEnded();
      await Promise.resolve();

      expect(service.getShoppingLists()()[1].name).toBe('Liste 2');
    });

    it('ngOnDestroy gibt den Puffer frei: zurückgestellte Updates werden angewandt, danach wirken Updates sofort', () => {
      page.onDragStarted();
      event('update-list', { id: 2, name: 'Neu', groupId: 1 });

      page.ngOnDestroy();
      event('update-list', { id: 3, name: 'Danach', groupId: 1 });

      expect(service.getShoppingLists()()[1].name).toBe('Neu');
      expect(service.getShoppingLists()()[2].name).toBe('Danach');
    });

    // Codex-Review F-002: nicht nur WebSocket-Events, auch GET-, REST- und PUT-Antworten ändern die Datenbasis
    it('GET entfernt A während des Drags (Listen): der Move rechnet gegen [A,B,C], danach wird die Löschung angewandt', async () => {
      service.getShoppingListsWithItems(wg, undefined, { silent: true });
      const get = http.expectOne(loadUrl(1));
      page.onDragStarted();
      get.flush(loadedLists().filter(list => list.id !== 1));

      expect(listIds()).toEqual([1, 2, 3]);

      page.onDragEnded();
      page.onListDropped(drop(3, 1));
      await Promise.resolve();

      const req = http.expectOne(`${api}/shopping-list/reorder`);
      expect(req.request.body.shoppingListIds).toEqual([1, 3, 2]);
      expect(listIds()).toEqual([3, 2]);
      req.flush({ groupId: 1, revision: 6, shoppingListIds: [3, 2] });
      expect(listIds()).toEqual([3, 2]);
    });

    it('GET entfernt Eintrag 11 während des Drags (Einträge): der Move rechnet gegen [11,12,13], danach wird die Löschung angewandt', async () => {
      service.getShoppingListsWithItems(wg, undefined, { silent: true });
      const get = http.expectOne(loadUrl(1));
      page.onDragStarted();
      get.flush(loadedLists().map(list => list.id === 1
        ? { ...list, shoppingItems: list.shoppingItems.filter(item => item.id !== 11) } : list));

      expect(itemIdsOfA()).toEqual([11, 12, 13]);

      page.onDragEnded();
      page.onItemDropped(listA(), drop(13, 1));
      await Promise.resolve();

      const req = http.expectOne(`${api}/shopping-list/reorder-items`);
      expect(req.request.body.shoppingItemIds).toEqual([11, 13, 12]);
      expect(itemIdsOfA()).toEqual([13, 12]);
      req.flush({ groupId: 1, shoppingListId: 1, revision: 3, shoppingItemIds: [13, 12] });
      expect(itemIdsOfA()).toEqual([13, 12]);
    });

    it('REST-Antwort (Eintrag angelegt) während des Drags wird erst nach dem Drop angewandt', () => {
      service.addItemToShoppingList({ id: null, name: 'Brot', completed: false, shoppingListId: 1, groupId: 1 });
      const add = http.expectOne(`${api}/shopping-list/add-item`);
      page.onDragStarted();
      add.flush({ id: 14, name: 'Brot', completed: false, shoppingListId: 1, groupId: 1 });

      expect(itemIdsOfA()).toEqual([11, 12, 13]);

      page.onDragEnded();
      page.onItemDropped(listA(), drop(13, 2));

      expect(itemIdsOfA()).toEqual([11, 12, 13, 14]);
    });

    it('Fehler-Antwort eines früheren Moves während des Drags: Toast sofort, Rücksprung und Refetch erst nach dem Drop', () => {
      const alerts = TestBed.inject(AlertService) as jasmine.SpyObj<AlertService>;
      page.onListDropped(drop(3, 0));
      const put = http.expectOne(`${api}/shopping-list/reorder`);
      expect(listIds()).toEqual([3, 1, 2]);

      page.onDragStarted();
      put.flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(alerts.showToast).toHaveBeenCalledOnceWith('alerts.shoppinglist.reorder.error');
      expect(listIds()).toEqual([3, 1, 2]);
      http.expectNone(loadUrl(1));

      page.onDragEnded();
      page.onListDropped(drop(3, 0));

      expect(listIds()).toEqual([1, 2, 3]);
      http.expectOne(loadUrl(1)).flush(loadedLists());
    });
  });
});
