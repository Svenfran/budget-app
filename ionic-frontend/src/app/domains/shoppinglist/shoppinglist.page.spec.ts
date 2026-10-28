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

  // Containertyp aus dem Aufruf: Listen-Drops (unknown) und Item-Drops (number)
  const drop = <T = unknown>(data: number, currentIndex: number) =>
    ({ item: { data }, currentIndex }) as unknown as CdkDragDrop<T, T, number>;
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

    // Verschieben zwischen Listen (DIV-2): die CDK-Container tragen die Listen-ID als Daten
    describe('in eine andere Liste', () => {
      const moveUrl = `${api}/shopping-list/move-item`;
      const dropBetween = (itemId: number, sourceListId: number, targetListId: number, currentIndex: number,
                           isPointerOverContainer = true) =>
        ({
          item: { data: itemId }, previousContainer: { data: sourceListId }, container: { data: targetListId }, currentIndex,
          isPointerOverContainer,
        }) as unknown as CdkDragDrop<number, number, number>;
      const listB = () => service.getShoppingLists()().find(list => list.id === 2)!;

      it('Codex F-001: losgelassen außerhalb des zuletzt betretenen Containers: kein Move, der Puffer wird freigegeben', () => {
        const move = spyOn(service, 'moveShoppingItemToList').and.callThrough();
        const release = spyOn(service, 'releaseUpdates').and.callThrough();

        page.onItemDropped(listB(), dropBetween(12, 1, 2, 0, false));

        expect(move).not.toHaveBeenCalled();
        expect(release).toHaveBeenCalled();
        http.expectNone(moveUrl);
        expect(itemIdsOfA()).toEqual([11, 12, 13]);
      });

      it('Drop in eine andere Liste: moveShoppingItemToList mit Eintrag, Quelle, Ziel und Index, danach Puffer frei', () => {
        const move = spyOn(service, 'moveShoppingItemToList').and.callThrough();
        const release = spyOn(service, 'releaseUpdates').and.callThrough();

        page.onItemDropped(listB(), dropBetween(12, 1, 2, 0));

        expect(move).toHaveBeenCalledOnceWith(jasmine.objectContaining({ id: 1 }), 12, 1, 2, 0);
        expect(release).toHaveBeenCalled();
        const req = http.expectOne(moveUrl);
        expect(req.request.body).toEqual({
          groupId: 1, shoppingItemId: 12, sourceShoppingListId: 1, targetShoppingListId: 2, targetShoppingItemIds: [12]
        });
        req.flush({
          groupId: 1, shoppingItemId: 12,
          source: { groupId: 1, shoppingListId: 1, revision: 3, shoppingItemIds: [11, 13] },
          target: { groupId: 1, shoppingListId: 2, revision: 3, shoppingItemIds: [12] },
        });
        expect(itemIdsOfA()).toEqual([11, 13]);
        expect(listB().shoppingItems.map(item => item.id)).toEqual([12]);
      });

      it('Drop in dieselbe Liste (gleicher Container): weiterhin reorder-items, kein move-item (AC8)', () => {
        const container = { data: 1 };
        const sameList = { item: { data: 13 }, previousContainer: container, container, currentIndex: 0 } as
          unknown as CdkDragDrop<number, number, number>;

        page.onItemDropped(listA(), sameList);

        http.expectNone(moveUrl);
        http.expectOne(`${api}/shopping-list/reorder-items`).flush({ groupId: 1, shoppingListId: 1, revision: 3, shoppingItemIds: [13, 11, 12] });
        expect(itemIdsOfA()).toEqual([13, 11, 12]);
      });

      it('Ziel inzwischen gelöscht bzw. Eintrag entfernt: kein Request, der Puffer wird trotzdem freigegeben', () => {
        const release = spyOn(service, 'releaseUpdates').and.callThrough();
        // bereits angewandte Löschungen, das CDK liefert aber noch die Container-Daten von vorher
        const staleB = listB();
        event('delete-list', { id: 2, name: 'Liste 2', groupId: 1 });
        event('delete-item', { id: 13, name: 'x', completed: false, shoppingListId: 1, groupId: 1 });

        page.onItemDropped(staleB, dropBetween(12, 1, 2, 0));
        page.onItemDropped(listA(), dropBetween(13, 1, 3, 0));

        http.expectNone(moveUrl);
        expect(release).toHaveBeenCalled();
        expect(listIds()).toEqual([1, 3]);
        expect(itemIdsOfA()).toEqual([11, 12]);
      });

      it('ein Eintrag mit offenem Move ist nicht ziehbar, nach der Antwort wieder', () => {
        /** Ist der `index`-te Eintrag im Container der Liste gesperrt (CDK-Klasse von `cdkDragDisabled`)? */
        const dragDisabled = (listId: number, index: number) => {
          fixture.detectChanges();
          const element = (fixture.nativeElement as HTMLElement).querySelectorAll(`#item-list-${listId} > ion-item`)[index];
          return element.classList.contains('cdk-drag-disabled');
        };

        page.onItemDropped(listB(), dropBetween(11, 1, 2, 0));

        expect(page.isMovePending(11)).toBeTrue();
        expect(dragDisabled(2, 0)).withContext('11, jetzt in B').toBeTrue();
        expect(dragDisabled(1, 0)).withContext('12 in A').toBeFalse();
        http.expectOne(moveUrl).flush('Server error', { status: 500, statusText: 'Server Error' });
        http.expectOne(loadUrl(1)).flush(loadedLists());
        expect(page.isMovePending(11)).toBeFalse();
        expect(dragDisabled(1, 0)).withContext('11, zurück in A').toBeFalse();
      });
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

// DIV-2: Verschieben zwischen Listen mit echter Maus-Geste (echtes CDK, reales Layout im Karma-Browser). Prüft, was die
// Handler-Tests nicht zeigen können: verbundene Item-Container, Ablagefläche leerer Listen, eingeklappte Listen als
// Nicht-Ziel und dass ein Listen-Drag keine Item-Container erreicht.
describe('ShoppinglistPage – Drag & Drop mit echter Geste', () => {
  let fixture: ComponentFixture<ShoppinglistPage>;
  let page: ShoppinglistPage;
  let http: HttpTestingController;
  const api = `${environment.apiBaseUrl}/api/groups`;
  const moveUrl = `${api}/shopping-list/move-item`;
  /** A (1: 11, 12), B (2: leer), C (3: 31, 32), D (4: 41); D bleibt eingeklappt. */
  const loadedLists = (): ShoppinglistDto[] => [[1, [11, 12]], [2, []], [3, [31, 32]], [4, [41]]].map(([id, items]) => ({
    id: id as number, name: `Liste ${id}`, listOrderRevision: 5, itemOrderRevision: 2,
    shoppingItems: (items as number[]).map(itemId => aShoppingItem({ id: itemId, name: `Artikel ${itemId}` })),
  }));

  type Point = { x: number; y: number };
  const el = (selector: string) => (fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLElement;
  const items = (listId: number) =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll(`#item-list-${listId} > ion-item`)) as HTMLElement[];
  const center = (element: Element): Point => {
    const rect = element.getBoundingClientRect();
    return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
  };
  const mouse = (target: EventTarget, type: string, point: Point) => target.dispatchEvent(new MouseEvent(type, {
    bubbles: true, cancelable: true, clientX: point.x, clientY: point.y, button: 0, buttons: type === 'mouseup' ? 0 : 1,
    // detail 0 hält das CDK für einen Screenreader-Klick und startet keinen Drag
    detail: 1,
  }));

  /**
   * Zieht `handle` mit der Maus: Druck, kleine Bewegung (startet den Drag), dann in Schritten zum Ziel, das erst nach
   * dem Start berechnet wird (die Ablagefläche leerer Listen entsteht erst mit dem Drag). `whileDragging` läuft vor
   * dem Loslassen; `via` ist ein Zwischenpunkt, über den der Weg führt. Jeder Punkt wird erst berechnet, wenn er
   * angesteuert wird: betritt der Eintrag eine andere Liste, verschiebt sich das Layout (wie ein Mensch folgt der Weg
   * dem Ziel).
   */
  async function drag(handle: HTMLElement, target: () => Point, whileDragging?: () => void, via?: () => Point) {
    const start = center(handle);
    mouse(handle, 'mousedown', start);
    mouse(document, 'mousemove', { x: start.x, y: start.y + 10 });
    let from = start;
    for (const next of via ? [via, target] : [target]) {
      const to = next();
      for (const step of [0.25, 0.5, 0.75, 1]) {
        mouse(document, 'mousemove', { x: from.x + (to.x - from.x) * step, y: from.y + (to.y - from.y) * step });
      }
      from = to;
    }
    const end = from;
    whileDragging?.();
    mouse(document, 'mouseup', end);
    // das CDK meldet den Drop erst nach der Rückanimation der Vorschau (200 ms, .cdk-drag-animating)
    await new Promise(resolve => setTimeout(resolve, 300));
    fixture.detectChanges();
  }

  /**
   * Ionic lädt seine Web Components im Testbrowser verzögert nach; solange ein Element hydriert, ist sein Inhalt nicht
   * im Layout. Vor der Geste daher warten, bis alle ion-Elemente der Seite fertig gerendert sind.
   */
  async function ionicReady() {
    const ionElements = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('*'))
      .filter(element => element.tagName.startsWith('ION-'));
    await Promise.all([...new Set(ionElements.map(element => element.tagName.toLowerCase()))]
      .map(tag => customElements.whenDefined(tag)));
    await Promise.all(ionElements.map(element => (element as HTMLElement & { componentOnReady?: () => Promise<unknown> })
      .componentOnReady?.()));
  }

  // Die Seite mit vier Listen ist höher als der Karma-Viewport; ohne sichtbare Ziele findet das CDK keinen Container
  // (elementFromPoint) und begrenzt die Vorschau auf den Scroll-Host. Nur für diesen Block: Karma-iframe erhöhen.
  const frame = window.frameElement as HTMLElement | null;
  const frameHeight = frame?.style.height ?? '';
  beforeAll(() => { if (frame) frame.style.height = '1600px'; });
  afterAll(() => { if (frame) frame.style.height = frameHeight; });

  beforeEach(async () => {
    spyOn(console, 'error');
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy)
      .and.returnValue(new BehaviorSubject<User | null>(aLoggedInUser({ id: 7 })));
    TestBed.configureTestingModule({
      declarations: [ShoppinglistPage],
      imports: [IonicModule.forRoot(), FormsModule, TranslateModule, DragDropModule],
      providers: [
        ...provideServiceTesting(),
        { provide: WebSocketService, useValue: jasmine.createSpyObj('WebSocketService', ['subscribe', 'unsubscribeOwner']) },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: auth },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(GroupService).setActiveGroup(aGroup({ id: 1, name: 'WG' }));
    fixture = TestBed.createComponent(ShoppinglistPage);
    page = fixture.componentInstance;
    // wie in der App füllt die Seite den Viewport (ion-content und Scroll-Host haben 100 % Höhe)
    (fixture.nativeElement as HTMLElement).style.cssText = 'position: fixed; inset: 0;';
    fixture.detectChanges();
    http.expectOne(`${api}/shopping-lists-with-items/1`).flush(loadedLists());
    page.toggleLists = { 1: true, 2: true, 3: true };
    fixture.detectChanges();
    await ionicReady();
  });

  afterEach(() => http.verify());

  /** Header-Zeile der n-ten Liste (Drag-Handle des Listen-Drags). */
  const listHeader = (index: number) =>
    (fixture.nativeElement as HTMLElement).querySelectorAll('ion-list .list-header')[index] as HTMLElement;
  const moved = (itemId: number, source: [number, number[]], target: [number, number[]]) => ({
    groupId: 1, shoppingItemId: itemId,
    source: { groupId: 1, shoppingListId: source[0], revision: 3, shoppingItemIds: source[1] },
    target: { groupId: 1, shoppingListId: target[0], revision: 3, shoppingItemIds: target[1] },
  });

  it('Eintrag aus A in die aufgeklappte leere Liste B: Ablagefläche nur während des Drags, PUT move-item mit Ziel B, Index 0', async () => {
    const scrollHost = el('.ion-content-scroll-host');
    expect(el('#item-list-2').getBoundingClientRect().height).withContext('ohne Drag keine Fläche').toBe(0);
    let flaecheWaehrendDrag = 0;
    let klasseWaehrendDrag = false;
    let inaktivUeberB = true;

    // Mitte der vorgesehenen 48-px-Fläche (ohne Fläche läge der Punkt unterhalb der Liste, nicht auf ihrer Kante)
    const flaechenMitte = () => {
      const rect = el('#item-list-2').getBoundingClientRect();
      return { x: rect.left + rect.width / 2, y: rect.top + 24 };
    };

    // über B hinein, dann zu Bs Mitte nach dem Wechsel (A ist dann einen Eintrag kürzer, B rückt nach oben)
    await drag(items(1)[0], flaechenMitte, () => {
      flaecheWaehrendDrag = el('#item-list-2').getBoundingClientRect().height;
      klasseWaehrendDrag = scrollHost.classList.contains('item-drag-active');
      inaktivUeberB = scrollHost.classList.contains('item-drop-outside');
    }, flaechenMitte);

    expect(klasseWaehrendDrag).toBeTrue();
    expect(inaktivUeberB).withContext('Zeiger über dem Ziel: Platzhalter aktiv').toBeFalse();
    expect(flaecheWaehrendDrag).toBeGreaterThanOrEqual(48);
    expect(scrollHost.classList.contains('item-drag-active')).toBeFalse();
    const req = http.expectOne(moveUrl);
    expect(req.request.body).toEqual({
      groupId: 1, shoppingItemId: 11, sourceShoppingListId: 1, targetShoppingListId: 2, targetShoppingItemIds: [11]
    });
    req.flush(moved(11, [1, [12]], [2, [11]]));
    expect(items(2).length).toBe(1);
    expect(items(1).length).toBe(1);
  });

  it('Eintrag aus A zwischen die zwei Einträge der aufgeklappten Liste C: Zielindex 1', async () => {
    // abwärts gezogen tauscht das CDK den Platzhalter mit dem Eintrag unter dem Zeiger: Ende auf 31 heißt nach 31
    await drag(items(1)[1], () => center(items(3)[0]));

    const req = http.expectOne(moveUrl);
    expect(req.request.body).toEqual(jasmine.objectContaining({
      shoppingItemId: 12, sourceShoppingListId: 1, targetShoppingListId: 3, targetShoppingItemIds: [31, 12, 32]
    }));
    req.flush(moved(12, [1, [11]], [3, [31, 12, 32]]));
  });

  it('eingeklappte Liste D wird nicht betreten: Drop auf ihrer Position verschiebt nichts', async () => {
    // B und C eingeklappt: der Weg von A nach D führt über keinen anderen aufgeklappten Container (das CDK ließe den
    // Eintrag sonst im zuletzt betretenen); gezogen wird der letzte Eintrag, damit A selbst nicht umsortiert
    page.toggleLists = { 1: true };
    fixture.detectChanges();

    let inaktiv = true;

    await drag(items(1)[1], () => center(listHeader(3)), () => {
      inaktiv = el('.ion-content-scroll-host').classList.contains('item-drop-outside');
    });

    // der Platzhalter steht in der Quelle A: dort gilt er auch außerhalb (Umsortieren wie DIV-1), bleibt also aktiv
    expect(inaktiv).toBeFalse();
    http.expectNone(moveUrl);
    http.expectNone(`${api}/shopping-list/reorder-items`);
    expect(items(1).length).toBe(2);
    expect(items(4).length).toBe(1);
  });

  it('Codex F-001: Weg über die aufgeklappte Liste B, losgelassen auf der eingeklappten Liste D: kein Move', async () => {
    page.toggleLists = { 1: true, 2: true };
    fixture.detectChanges();
    let platzhalterIn: string | undefined;
    let inaktivBeimLoslassen = false;

    await drag(items(1)[1], () => center(listHeader(3)), () => {
      platzhalterIn = document.querySelector('.cdk-drag-placeholder')?.parentElement?.id;
      inaktivBeimLoslassen = el('.ion-content-scroll-host').classList.contains('item-drop-outside');
    }, () => {
      const rect = el('#item-list-2').getBoundingClientRect();
      return { x: rect.left + rect.width / 2, y: rect.top + 24 };
    });

    // B ist der zuletzt betretene Container (das CDK lässt den Platzhalter dort), losgelassen wurde über D; der
    // Platzhalter in B ist deshalb als inaktiv markiert, nach dem Drop nicht mehr
    expect(platzhalterIn).toBe('item-list-2');
    expect(inaktivBeimLoslassen).toBeTrue();
    expect(el('.ion-content-scroll-host').classList.contains('item-drop-outside')).toBeFalse();
    http.expectNone(moveUrl);
    http.expectNone(`${api}/shopping-list/reorder-items`);
    expect(items(1).length).toBe(2);
    expect(items(2).length).toBe(0);
  });

  it('Codex-Review 2 F-001: B betreten und seitlich verlassen: Platzhalter inaktiv, Loslassen verschiebt nicht', async () => {
    // die Item-Container sperren die x-Achse (cdkDropListLockAxis): das CDK meldet beim Bewegen das x beim Aufnehmen,
    // entscheidet beim Drop aber mit der tatsächlichen Zeigerposition
    page.toggleLists = { 1: true, 2: true };
    fixture.detectChanges();
    const flaechenMitte = () => {
      const rect = el('#item-list-2').getBoundingClientRect();
      return { x: rect.left + rect.width / 2, y: rect.top + 24 };
    };
    const rechtsNeben = () => {
      const rect = el('#item-list-2').getBoundingClientRect();
      return { x: rect.right + 10, y: rect.top + 24 };
    };
    let platzhalterIn: string | undefined;
    let inaktivBeimLoslassen = false;

    await drag(items(1)[1], rechtsNeben, () => {
      platzhalterIn = document.querySelector('.cdk-drag-placeholder')?.parentElement?.id;
      inaktivBeimLoslassen = el('.ion-content-scroll-host').classList.contains('item-drop-outside');
    }, flaechenMitte);

    expect(platzhalterIn).toBe('item-list-2');
    expect(inaktivBeimLoslassen).toBeTrue();
    http.expectNone(moveUrl);
    expect(items(1).length).toBe(2);
    expect(el('.ion-content-scroll-host').classList.contains('item-drop-outside')).toBeFalse();
  });

  it('Listen-Drag über die Item-Container: nur die Listenreihenfolge ändert sich, kein move-item', async () => {
    await drag(listHeader(2), () => center(items(1)[0]));

    http.expectNone(moveUrl);
    http.expectNone(`${api}/shopping-list/reorder-items`);
    const reorder = http.expectOne(`${api}/shopping-list/reorder`);
    expect(reorder.request.body.shoppingListIds).toEqual([3, 1, 2, 4]);
    reorder.flush({ groupId: 1, revision: 6, shoppingListIds: [3, 1, 2, 4] });
  });
});
