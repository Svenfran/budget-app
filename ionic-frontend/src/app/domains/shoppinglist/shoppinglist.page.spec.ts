import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { HttpTestingController } from '@angular/common/http/testing';
import { signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import {
  AlertButton, AlertController, AlertOptions, IonicModule, LoadingController, ModalController, ModalOptions
} from '@ionic/angular';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
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
import { SuggestionsSheetProps } from './suggestions-sheet/suggestions-sheet-options';
import { SuggestionsSheetComponent } from './suggestions-sheet/suggestions-sheet.component';

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

  // Containertyp aus dem Aufruf: Listen-Drops (unknown) und Item-Drops (number); losgelassen über dem Container
  const drop = <T = unknown>(data: number, currentIndex: number) =>
    ({ item: { data }, currentIndex, isPointerOverContainer: true }) as unknown as CdkDragDrop<T, T, number>;
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
        const sameList = { item: { data: 13 }, previousContainer: container, container, currentIndex: 0, isPointerOverContainer: true } as
          unknown as CdkDragDrop<number, number, number>;

        page.onItemDropped(listA(), sameList);

        http.expectNone(moveUrl);
        http.expectOne(`${api}/shopping-list/reorder-items`).flush({ groupId: 1, shoppingListId: 1, revision: 3, shoppingItemIds: [13, 11, 12] });
        expect(itemIdsOfA()).toEqual([13, 11, 12]);
      });

      it('in derselben Liste, aber außerhalb losgelassen: Abbruch ohne reorder-items, der Puffer wird freigegeben', () => {
        const move = spyOn(service, 'moveShoppingItem').and.callThrough();
        const release = spyOn(service, 'releaseUpdates').and.callThrough();
        const container = { data: 1 };
        const outside = { item: { data: 11 }, previousContainer: container, container, currentIndex: 2, isPointerOverContainer: false } as
          unknown as CdkDragDrop<number, number, number>;

        page.onItemDropped(listA(), outside);

        expect(move).not.toHaveBeenCalled();
        expect(release).toHaveBeenCalled();
        http.expectNone(`${api}/shopping-list/reorder-items`);
        expect(itemIdsOfA()).toEqual([11, 12, 13]);
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
      add.flush({ id: 14, name: 'Brot', completed: false, shoppingListId: 1, groupId: 1 }, { status: 201, statusText: 'Created' });

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

  // DIV-23 / DIV-69: nutzt den Aufbau dieses Blocks (echter ShoppinglistService, HttpTestingController, Gruppen- und
  // Sitzungswechsel); Loading und Modal sind Fakes. Das Sheet selbst (Auswahl, Hinzufügen, Schließen) testet
  // suggestions-sheet.component.spec.ts; hier geht es um das Öffnen und den mitgegebenen Kontext.
  describe('Einkaufsvorschläge (DIV-23, Bottom Sheet DIV-69)', () => {
    const suggestionsUrl = `${api}/shopping-list/suggestions/1/1`;
    const addItemsUrl = `${api}/shopping-list/add-items`;
    const vorschlaege = (names: string[], defaultSuggestions = false) =>
      ({ groupId: 1, shoppingListId: 1, defaultSuggestions, names });
    let sheets: ModalOptions[];
    let praesentiert: number;
    let entfernt: number;
    let alerts: jasmine.SpyObj<AlertService>;
    let alertCreate: jasmine.Spy;
    let modalCreate: jasmine.Spy;
    let loadingCreate: jasmine.Spy;

    const sheet = () => sheets[sheets.length - 1];
    const props = () => sheet().componentProps as unknown as SuggestionsSheetProps & { adding: WritableSignal<boolean> };
    const modalEl = () => ({
      present: () => { praesentiert++; return Promise.resolve(); },
      remove: () => { entfernt++; },
    }) as unknown as HTMLIonModalElement;
    /** create() und present() hängen an Promises; einige Mikrotasks abwarten. */
    const mikrotasks = async () => { for (let i = 0; i < 5; i++) await Promise.resolve(); };
    const oeffnen = async (names: string[], defaultSuggestions = false) => {
      page.onShowSuggestions(listA());
      http.expectOne(suggestionsUrl).flush(vorschlaege(names, defaultSuggestions));
      await mikrotasks();
    };

    beforeEach(() => {
      sheets = [];
      praesentiert = 0;
      entfernt = 0;
      alerts = TestBed.inject(AlertService) as jasmine.SpyObj<AlertService>;
      loadingCreate = spyOn(TestBed.inject(LoadingController), 'create').and.callThrough();
      alertCreate = spyOn(TestBed.inject(AlertController), 'create').and.callThrough();
      modalCreate = spyOn(TestBed.inject(ModalController), 'create').and.callFake((options: ModalOptions) => {
        sheets.push(options);
        return Promise.resolve(modalEl());
      });
    });

    it('Öffnen: GET für Gruppe und Liste, dann das Bottom Sheet statt eines Alerts (AC 1)', async () => {
      await oeffnen(['Milch', 'Brot', 'Eier']);

      expect(sheets.length).toBe(1);
      expect(sheet().component).toBe(SuggestionsSheetComponent);
      expect(sheet().cssClass).toBe('bottom-sheet');
      expect(sheet().breakpoints).toEqual([0, 0.75, 1]);
      expect(praesentiert).toBe(1);
      expect(alertCreate).not.toHaveBeenCalled();
    });

    it('übergibt alle Vorschläge unverändert, Listenname, Gruppe, Liste und ein freies adding-Signal (AC 2)', async () => {
      await oeffnen(['Milch', 'Brot', 'Eier']);

      expect(props().suggestions).toEqual(vorschlaege(['Milch', 'Brot', 'Eier']));
      expect(props().listName).toBe('Liste 1');
      expect(props().groupId).toBe(1);
      expect(props().listId).toBe(1);
      expect(props().adding()).toBeFalse();
      expect(props().inContext()).toBeTrue();
    });

    it('Standardvorschläge: Kennzeichen wird an das Sheet weitergegeben', async () => {
      await oeffnen(['Milch', 'Tomaten'], true);

      expect(props().suggestions.defaultSuggestions).toBeTrue();
    });

    it('keine Vorschläge: Sheet öffnet mit leerer Liste (Leerzustand im Sheet)', async () => {
      await oeffnen([]);

      expect(praesentiert).toBe(1);
      expect(props().suggestions.names).toEqual([]);
    });

    describe('Ladeanzeige im Chip statt Overlay', () => {
      const chip = () => (fixture.nativeElement as HTMLElement).querySelector('.suggestions-chip') as HTMLIonChipElement;

      beforeEach(() => {
        page.toggleLists = { 1: true };
        fixture.detectChanges();
      });

      it('während des GET: Spinner statt Icon, Chip gesperrt, kein Lade-Overlay', () => {
        page.onShowSuggestions(listA());
        fixture.detectChanges();

        expect(page.loadingSuggestions().has(1)).toBeTrue();
        expect(chip().disabled).toBeTrue();
        expect(chip().getAttribute('aria-busy')).toBe('true');
        expect(chip().querySelector('ion-spinner')).not.toBeNull();
        expect(chip().querySelector('ion-icon')).toBeNull();
        expect(loadingCreate).not.toHaveBeenCalled();
        http.expectOne(suggestionsUrl).flush(vorschlaege(['Milch']));
      });

      it('nach dem Laden: Icon zurück, Chip wieder aktiv', async () => {
        await oeffnen(['Milch']);
        fixture.detectChanges();

        expect(page.loadingSuggestions().size).toBe(0);
        expect(chip().disabled).toBeFalse();
        expect(chip().querySelector('ion-spinner')).toBeNull();
        expect(chip().querySelector('ion-icon')).not.toBeNull();
      });

      it('nach einem Ladefehler: Chip wieder aktiv', async () => {
        page.onShowSuggestions(listA());
        http.expectOne(suggestionsUrl).flush('Server error', { status: 500, statusText: 'Server Error' });
        await mikrotasks();
        fixture.detectChanges();

        expect(page.loadingSuggestions().size).toBe(0);
        expect(chip().disabled).toBeFalse();
      });

      it('erneutes Tippen während des Ladens: nur ein GET, nur ein Sheet', async () => {
        page.onShowSuggestions(listA());
        page.onShowSuggestions(listA());
        http.expectOne(suggestionsUrl).flush(vorschlaege(['Milch']));
        await mikrotasks();

        expect(sheets.length).toBe(1);
      });

      it('Gruppenwechsel während des GET: Ladezustand endet trotzdem', async () => {
        page.onShowSuggestions(listA());
        const req = http.expectOne(suggestionsUrl);
        groupService.setActiveGroup(urlaub);
        fixture.detectChanges();
        http.expectOne(loadUrl(2)).flush([aShoppingList({ id: 1 })]);
        req.flush(vorschlaege(['Milch']));
        await mikrotasks();

        expect(page.loadingSuggestions().size).toBe(0);
      });
    });

    it('Fehler beim Laden: Toast, kein Sheet', async () => {
      page.onShowSuggestions(listA());
      http.expectOne(suggestionsUrl).flush('Server error', { status: 500, statusText: 'Server Error' });
      await mikrotasks();

      expect(alerts.showToast).toHaveBeenCalledOnceWith('alerts.shoppinglist.suggestions.error');
      expect(modalCreate).not.toHaveBeenCalled();
    });

    it('Gruppenwechsel während des GET: kein Sheet und kein Toast (DIV-23 F-002)', async () => {
      page.onShowSuggestions(listA());
      const req = http.expectOne(suggestionsUrl);

      groupService.setActiveGroup(urlaub);
      fixture.detectChanges();
      http.expectOne(loadUrl(2)).flush([aShoppingList({ id: 1 })]);
      req.flush(vorschlaege(['Milch']));
      await mikrotasks();

      expect(modalCreate).not.toHaveBeenCalled();
      expect(alerts.showToast).not.toHaveBeenCalled();
    });

    it('Kontext weg, bevor create() fertig ist: Sheet wird entfernt statt gezeigt', async () => {
      let erzeugt!: (el: HTMLIonModalElement) => void;
      modalCreate.and.callFake((options: ModalOptions) => {
        sheets.push(options);
        return new Promise<HTMLIonModalElement>(resolve => erzeugt = resolve);
      });
      await oeffnen(['Milch']);

      groupService.setActiveGroup(urlaub);
      fixture.detectChanges();
      http.expectOne(loadUrl(2)).flush([aShoppingList({ id: 1 })]);
      erzeugt(modalEl());
      await mikrotasks();

      expect(praesentiert).toBe(0);
      expect(entfernt).toBe(1);
    });

    // Bestehende Regressionstests (DIV-23 F-002), jetzt über den Kontext, den das Sheet erhält: ist er ungültig, sendet
    // das Sheet beim Bestätigen nichts und schließt (Nachweis in suggestions-sheet.component.spec.ts).
    describe('Kontext bei geöffnetem Sheet', () => {
      it('Gruppenwechsel: Kontext ungültig, auch nach dem Laden der neuen Gruppe', async () => {
        await oeffnen(['Milch']);

        groupService.setActiveGroup(urlaub);
        fixture.detectChanges();
        http.expectOne(loadUrl(2)).flush([aShoppingList({ id: 1 })]);

        expect(props().inContext()).toBeFalse();
        http.expectNone(addItemsUrl);
      });

      it('Logout und Login mit derselben Gruppe: Kontext ungültig', async () => {
        await oeffnen(['Milch']);

        session.next(null);
        session.next(aLoggedInUser({ id: 7 }));
        groupService.setActiveGroup(wg);
        fixture.detectChanges();
        http.expectOne(loadUrl(1)).flush(loadedLists());

        expect(props().inContext()).toBeFalse();
        http.expectNone(addItemsUrl);
      });

      it('Liste inzwischen gelöscht (delete-list): Kontext ungültig', async () => {
        await oeffnen(['Milch']);

        event('delete-list', { id: 1, name: 'Liste 1', groupId: 1 });

        expect(props().inContext()).toBeFalse();
        http.expectNone(addItemsUrl);
      });
    });
  });

  describe('Alle Einträge löschen (Overflow-Menü)', () => {
    const clearUrl = `${api}/shopping-list/clear-items`;
    let dialoge: AlertOptions[];
    let translate: TranslateService;

    const dialog = () => dialoge[dialoge.length - 1];
    const okButton = () => (dialog().buttons as AlertButton[]).find(button => button.role !== 'cancel')!;
    const bestaetigen = () => (okButton().handler as () => void)();
    const listB = () => service.getShoppingLists()().find(list => list.id === 2)!;
    const host = () => fixture.nativeElement as HTMLElement;
    /** Aktionszeile der Liste (alle Listen sind im DOM, eingeklappte nur per [hidden]). */
    const aktionszeile = (index: number) => host().querySelectorAll('.chip')[index] as HTMLElement;

    beforeEach(() => {
      dialoge = [];
      translate = TestBed.inject(TranslateService);
      spyOn(translate, 'instant').and.callThrough();
      spyOn(service, 'deleteAllShoppingItems').and.callThrough();
      spyOn(TestBed.inject(AlertController), 'create').and.callFake((options?: AlertOptions) => {
        dialoge.push(options!);
        return Promise.resolve({ present: () => Promise.resolve() } as unknown as HTMLIonAlertElement);
      });
    });

    it('Dialog: Hinweis auf offene Einträge mit Listenname, Abbrechen und roter Löschen-Button', async () => {
      await page.onDeleteAllItems(1);

      expect(dialoge.length).toBe(1);
      expect(dialog().header).toBe('alerts.shoppinglist.delete_all_items.header');
      expect(translate.instant).toHaveBeenCalledWith('alerts.shoppinglist.delete_all_items.message', { listName: 'Liste 1' });
      expect((dialog().buttons as AlertButton[]).map(button => button.role)).toEqual(['cancel', 'destructive']);
      expect(okButton().cssClass).toBe('alert-button-danger');
    });

    it('Bestätigen: genau ein POST clear-items mit allen Einträgen der Liste, danach ist sie leer, die anderen bleiben', async () => {
      await page.onDeleteAllItems(1);

      bestaetigen();
      const req = http.expectOne(clearUrl);
      expect(req.request.body).toEqual([11, 12, 13].map(id => jasmine.objectContaining({ id, shoppingListId: 1, groupId: 1 })));
      req.flush(null);

      expect(itemIdsOfA()).toEqual([]);
      expect(listIds()).toEqual([1, 2, 3]);
    });

    it('Änderungen zwischen Öffnen und Bestätigen: gesendet wird der aktuelle Stand', async () => {
      await page.onDeleteAllItems(1);

      event('add-item', { id: 14, name: 'Kaffee', completed: false, shoppingListId: 1, groupId: 1 });
      event('delete-item', { id: 11, name: 'x', completed: false, shoppingListId: 1, groupId: 1 });
      bestaetigen();

      const req = http.expectOne(clearUrl);
      expect(req.request.body.map((item: { id: number }) => item.id)).toEqual([12, 13, 14]);
      req.flush(null);
    });

    it('Abbrechen: der Abbrechen-Button hat keine Aktion, es geht kein Request raus', async () => {
      await page.onDeleteAllItems(1);

      const cancel = (dialog().buttons as AlertButton[]).find(button => button.role === 'cancel')!;
      expect(cancel.handler).toBeUndefined();
      http.expectNone(clearUrl);
      expect(service.deleteAllShoppingItems).not.toHaveBeenCalled();
    });

    it('leere Liste: kein Dialog, kein Request', async () => {
      await page.onDeleteAllItems(2);

      expect(dialoge).toEqual([]);
      http.expectNone(clearUrl);
    });

    describe('veralteter Dialog (Kontextschutz)', () => {
      it('Gruppenwechsel vor dem Bestätigen: kein Request', async () => {
        await page.onDeleteAllItems(1);

        groupService.setActiveGroup(urlaub);
        fixture.detectChanges();
        http.expectOne(loadUrl(2)).flush(loadedLists());
        bestaetigen();

        http.expectNone(clearUrl);
        expect(service.deleteAllShoppingItems).not.toHaveBeenCalled();
      });

      it('Logout und Login mit derselben Gruppe vor dem Bestätigen: kein Request', async () => {
        await page.onDeleteAllItems(1);

        session.next(null);
        session.next(aLoggedInUser({ id: 7 }));
        groupService.setActiveGroup(wg);
        fixture.detectChanges();
        http.expectOne(loadUrl(1)).flush(loadedLists());
        bestaetigen();

        http.expectNone(clearUrl);
        expect(service.deleteAllShoppingItems).not.toHaveBeenCalled();
      });

      it('Liste inzwischen gelöscht (delete-list): kein Request', async () => {
        await page.onDeleteAllItems(1);

        event('delete-list', { id: 1, name: 'Liste 1', groupId: 1 });
        bestaetigen();

        http.expectNone(clearUrl);
        expect(service.deleteAllShoppingItems).not.toHaveBeenCalled();
      });

      it('Liste bei offenem Dialog vollständig geleert (delete-all-items): kein Request', async () => {
        await page.onDeleteAllItems(1);

        event('delete-all-items', [11, 12, 13].map(id => ({ id, name: 'x', completed: true, shoppingListId: 1, groupId: 1 })));
        bestaetigen();

        http.expectNone(clearUrl);
        expect(service.deleteAllShoppingItems).not.toHaveBeenCalled();
      });

      it('Gruppenwechsel, während der Dialog noch erzeugt wird: er wird nicht angezeigt, kein Request (Codex-Review F-001)', async () => {
        const alertEl = jasmine.createSpyObj<HTMLIonAlertElement>('alert', ['present', 'remove']);
        alertEl.present.and.resolveTo();
        let erzeugt!: (el: HTMLIonAlertElement) => void;
        (TestBed.inject(AlertController).create as jasmine.Spy).and.callFake((options?: AlertOptions) => {
          dialoge.push(options!);
          return new Promise<HTMLIonAlertElement>(resolve => erzeugt = resolve);
        });

        const geoeffnet = page.onDeleteAllItems(1);
        groupService.setActiveGroup(urlaub);
        fixture.detectChanges();
        http.expectOne(loadUrl(2)).flush(loadedLists());
        erzeugt(alertEl);
        await geoeffnet;

        expect(alertEl.present).not.toHaveBeenCalled();
        expect(alertEl.remove).toHaveBeenCalled();
        bestaetigen();
        http.expectNone(clearUrl);
        expect(service.deleteAllShoppingItems).not.toHaveBeenCalled();
      });

      it('ohne Kontextwechsel wird der erzeugte Dialog angezeigt', async () => {
        const alertEl = jasmine.createSpyObj<HTMLIonAlertElement>('alert', ['present', 'remove']);
        alertEl.present.and.resolveTo();
        (TestBed.inject(AlertController).create as jasmine.Spy).and.resolveTo(alertEl);

        await page.onDeleteAllItems(1);

        expect(alertEl.present).toHaveBeenCalledTimes(1);
        expect(alertEl.remove).not.toHaveBeenCalled();
      });
    });

    describe('Menü-Button und Popover', () => {
      it('Klick auf den Menü-Button öffnet das Menü für diese Liste, klappt aber nichts auf oder zu', () => {
        const toggle = spyOn(page, 'toggleList').and.callThrough();
        fixture.detectChanges();

        (aktionszeile(0).querySelector('.list-menu-button') as HTMLElement).click();

        expect(page.listMenuOpen).toBeTrue();
        expect(page.listMenuListId).toBe(1);
        expect(toggle).not.toHaveBeenCalled();
        expect(page.toggleLists[1]).toBeFalsy();
      });

      it('Menüpunkt löscht über denselben Dialog die Liste des Menüs', async () => {
        page.openListMenu(new MouseEvent('click'), listA());
        page.onDeleteAllItemsFromMenu();
        await Promise.resolve();

        expect(dialoge.length).toBe(1);
        bestaetigen();
        http.expectOne(clearUrl).flush(null);
        expect(itemIdsOfA()).toEqual([]);
      });

      it('die Menüliste kommt aus dem aktuellen State: leer bzw. gelöscht wird erkannt', () => {
        page.openListMenu(new MouseEvent('click'), listB());
        expect(page.menuList()?.shoppingItems.length).toBe(0);

        page.openListMenu(new MouseEvent('click'), listA());
        event('delete-list', { id: 1, name: 'Liste 1', groupId: 1 });
        expect(page.menuList()).toBeUndefined();
      });
    });

    it('Aktionszeile: Vorschläge und Menü-Button, kein eigener Chip „Erledigte löschen“ mehr', () => {
      fixture.detectChanges();

      for (const zeile of [aktionszeile(0), aktionszeile(1)]) {
        expect(zeile.querySelector('.list-menu-button')).not.toBeNull();
        expect(zeile.textContent).toContain('domains.shoppinglists.suggestions');
        expect(zeile.textContent).not.toContain('domains.shoppinglists.delete_completed');
      }
    });
  });

  // Gerendertes Popover: Menüpunkte und ihr Disabled-Zustand
  describe('Overflow-Menü gerendert', () => {
    const host = () => fixture.nativeElement as HTMLElement;
    const aktionszeile = (index: number) => host().querySelectorAll('.chip')[index] as HTMLElement;
    const popover = () => document.querySelector('ion-popover.list-menu') as HTMLIonPopoverElement;
    const menuepunkte = () => Array.from(popover().querySelectorAll('ion-item')) as HTMLIonItemElement[];
    const punkt = (cls: string) => popover().querySelector(`ion-item.${cls}`) as HTMLIonItemElement | null;

    /** Klickt den Menü-Button der Liste und wartet, bis Ionic das Popover samt Inhalt dargestellt hat. */
    const menuOeffnen = async (index: number) => {
      fixture.detectChanges();
      (aktionszeile(index).querySelector('.list-menu-button') as HTMLElement).click();
      fixture.detectChanges();
      for (let i = 0; i < 100 && menuepunkte().length === 0; i++) {
        await new Promise(resolve => setTimeout(resolve, 20));
        fixture.detectChanges();
      }
      expect(menuepunkte().length).toBeGreaterThan(0);
    };

    // Ionic hängt das dargestellte Popover außerhalb der Fixture ein: schließen und entfernen, sonst verdeckt sein
    // Backdrop die Geste-Tests und spätere Tests fänden das alte Popover
    const aufraeumen = async () => {
      for (const el of Array.from(document.querySelectorAll('ion-popover.list-menu')) as HTMLIonPopoverElement[]) {
        await el.dismiss().catch(() => undefined);
        el.remove();
      }
    };

    afterEach(aufraeumen);

    it('Menü mit beiden Löschaktionen; „Alle Einträge löschen“ ist bei Einträgen aktiv', async () => {
      await menuOeffnen(0);

      expect(menuepunkte().map(item => item.textContent!.trim())).toEqual([
        'domains.shoppinglists.delete_completed', 'domains.shoppinglists.delete_all_items',
      ]);
      expect(punkt('list-menu-delete-all')!.disabled).toBeFalse();
    });

    it('leere Liste: beide Löschaktionen ausgegraut', async () => {
      await menuOeffnen(1);

      expect(punkt('list-menu-delete-completed')!.disabled).toBeTrue();
      expect(punkt('list-menu-delete-all')!.disabled).toBeTrue();
    });

    it('nur offene Einträge → „Erledigte löschen“ ausgegraut und ohne Request; nach dem Erledigen aktiv und löscht nur Erledigte', async () => {
      spyOn(service, 'deleteAllCompletedShoppingItems').and.callThrough();

      await menuOeffnen(0);
      expect(punkt('list-menu-delete-completed')!.disabled).toBeTrue();
      page.onDeleteCompletedFromMenu();
      http.expectNone(`${api}/shopping-list/delete-all-items`);

      event('update-item', { id: 12, name: 'Brot', completed: true, shoppingListId: 1, groupId: 1 });
      fixture.detectChanges();
      expect(punkt('list-menu-delete-completed')!.disabled).toBeFalse();

      punkt('list-menu-delete-completed')!.click();
      const req = http.expectOne(`${api}/shopping-list/delete-all-items`);
      expect(req.request.body).toEqual([{ id: 12, name: 'Brot', completed: true, shoppingListId: 1, groupId: 1 }]);
      req.flush(null);
      expect(service.deleteAllCompletedShoppingItems).toHaveBeenCalledTimes(1);
      expect(itemIdsOfA()).toEqual([11, 13]);
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
    // B und C eingeklappt: der Weg von A nach D führt über keinen anderen aufgeklappten Container
    page.toggleLists = { 1: true };
    fixture.detectChanges();

    let inaktiv = false;

    await drag(items(1)[1], () => center(listHeader(3)), () => {
      inaktiv = el('.ion-content-scroll-host').classList.contains('item-drop-outside');
    });

    // der Platzhalter steht in der Quelle A, der Zeiger über D: Abbruch, deshalb inaktiv dargestellt
    expect(inaktiv).toBeTrue();
    http.expectNone(moveUrl);
    http.expectNone(`${api}/shopping-list/reorder-items`);
    expect(items(1).length).toBe(2);
    expect(items(4).length).toBe(1);
  });

  it('Codex-Review 4: erster Eintrag innerhalb von A ans Ende sortiert: Platzhalter aktiv, reorder-items, neue Endposition', async () => {
    page.toggleLists = { 1: true };
    fixture.detectChanges();
    // vor dem Start messen: während des Drags hängt das gezogene Element nicht mehr im Container
    const position12 = center(items(1)[1]);
    let inaktiv = true;

    await drag(items(1)[0], () => position12, () => {
      inaktiv = el('.ion-content-scroll-host').classList.contains('item-drop-outside');
    });

    expect(inaktiv).toBeFalse();
    http.expectNone(moveUrl);
    const reorder = http.expectOne(`${api}/shopping-list/reorder-items`);
    expect(reorder.request.body.shoppingItemIds).toEqual([12, 11]);
    reorder.flush({ groupId: 1, shoppingListId: 1, revision: 3, shoppingItemIds: [12, 11] });
    fixture.detectChanges();
    expect(items(1)[1].textContent).toContain('Artikel 11');
  });

  it('PR #46: erster Eintrag über den zweiten hinweg aus A heraus gezogen: Abbruch, er bleibt an Position 1', async () => {
    // das CDK sortiert den Platzhalter beim Überqueren von 12 hinter 12 und lässt ihn beim Verlassen von A dort; ohne
    // Abbruchregel würde 11 beim Loslassen ans Ende von A gespeichert
    page.toggleLists = { 1: true };
    fixture.detectChanges();
    // vor dem Start messen: während des Drags hängt das gezogene Element nicht mehr im Container
    const position12 = center(items(1)[1]);
    const ueber12 = () => position12;
    const nebenA = () => {
      const rect = el('#item-list-1').getBoundingClientRect();
      return { x: rect.right + 10, y: rect.bottom + 60 };
    };
    let inaktiv = false;

    await drag(items(1)[0], nebenA, () => {
      inaktiv = el('.ion-content-scroll-host').classList.contains('item-drop-outside');
    }, ueber12);

    expect(inaktiv).toBeTrue();
    http.expectNone(`${api}/shopping-list/reorder-items`);
    http.expectNone(moveUrl);
    expect(TestBed.inject(ShoppinglistService).getShoppingLists()()[0].shoppingItems.map(item => item.id)).toEqual([11, 12]);
    expect(items(1)[0].textContent).toContain('Artikel 11');
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
