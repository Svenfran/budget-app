import { fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { tokenStorageFake } from 'src/testing/test-providers';
import { SessionStore, withinBudget } from './session-store';
import { StoredSession } from './token-storage';

const session = (refreshToken: string): StoredSession => ({
  refreshToken, userId: 7, name: 'Anna', email: 'anna@example.de', sessionExpiresAt: 1757894400000,
});

describe('SessionStore', () => {
  let storage: ReturnType<typeof tokenStorageFake>;
  let generation: number;
  let current: StoredSession | null;
  let store: SessionStore;

  beforeEach(() => {
    storage = tokenStorageFake({ manual: true });
    generation = 1;
    current = session('A');
    store = new SessionStore(storage, g => g === generation, () => current);
    spyOn(console, 'warn');
  });

  it('startet einen Zugriff erst, wenn der vorherige beendet ist (keine Überlappung)', fakeAsync(() => {
    store.save(1);
    store.clear();
    flushMicrotasks();

    expect(storage.calls()).toEqual(['save:A']);

    storage.resolveNext();
    flushMicrotasks();
    expect(storage.calls()).toEqual(['save:A', 'clear']);
  }));

  it('Reihenfolge save(A) hängt -> clear -> save(B): am Ende steht B, kein weiteres clear danach', fakeAsync(() => {
    store.save(1);                    // save(A) startet und hängt
    flushMicrotasks();
    generation = 2;                   // Logout
    store.clear();
    generation = 3;                   // Login B
    current = session('B');
    store.save(3);
    flushMicrotasks();
    expect(storage.calls()).toEqual(['save:A']);

    storage.resolveNext();            // save(A) endet irgendwann
    flushMicrotasks();
    storage.resolveNext();            // clear
    flushMicrotasks();
    storage.resolveNext();            // save(B)
    flushMicrotasks();

    expect(storage.calls()).toEqual(['save:A', 'clear', 'save:B']);
    expect(storage.stored()).toEqual(session('B'));
  }));

  it('dasselbe ohne neuen Login: Speicher am Ende leer', fakeAsync(() => {
    store.save(1);
    flushMicrotasks();
    generation = 2;
    store.clear();
    flushMicrotasks();

    storage.resolveNext();
    flushMicrotasks();
    storage.resolveNext();
    flushMicrotasks();

    expect(storage.calls()).toEqual(['save:A', 'clear']);
    expect(storage.stored()).toBeNull();
  }));

  it('überspringt ein save, dessen Generation beim Start nicht mehr aktuell ist', fakeAsync(() => {
    store.clear();                    // hängt
    store.save(1);
    generation = 2;
    flushMicrotasks();

    storage.resolveNext();
    flushMicrotasks();

    expect(storage.calls()).toEqual(['clear']);
  }));

  it('save schreibt den beim Start aktuellen Stand, nicht den beim Einreihen', fakeAsync(() => {
    store.clear();                    // hängt
    store.save(1);
    flushMicrotasks();
    current = { ...session('A2'), name: 'Anna Neu' };
    storage.resolveNext();
    flushMicrotasks();
    storage.resolveNext();
    flushMicrotasks();

    expect(storage.stored()).toEqual({ ...session('A2'), name: 'Anna Neu' });
  }));

  it('ein abgelehnter Zugriff wird geloggt und blockiert die Kette nicht', fakeAsync(() => {
    let saveErgebnis = 'offen';
    store.save(1).then(() => saveErgebnis = 'erfüllt');
    store.clear();
    flushMicrotasks();

    storage.rejectNext(new Error('io'));
    flushMicrotasks();
    storage.resolveNext();
    flushMicrotasks();

    expect(saveErgebnis).toBe('erfüllt');
    expect(storage.calls()).toEqual(['save:A', 'clear']);
    expect(console.warn).toHaveBeenCalledWith('Session-Speicher: save fehlgeschlagen', 'Error');
  }));

  it('load liefert bei einem Lesefehler null', fakeAsync(() => {
    let geladen: StoredSession | null | undefined;
    store.load().then(value => geladen = value);
    flushMicrotasks();

    storage.rejectNext(new Error('io'));
    flushMicrotasks();

    expect(geladen).toBeNull();
  }));
});

describe('withinBudget', () => {
  it('liefert den Wert, wenn das Promise innerhalb des Budgets erfüllt wird', fakeAsync(() => {
    let ergebnis: unknown;
    withinBudget(Promise.resolve('x'), 5000).then(r => ergebnis = r);
    flushMicrotasks();

    expect(ergebnis).toEqual({ done: true, value: 'x' });
    tick(5000);
  }));

  it('meldet nach Ablauf des Budgets "nicht fertig", auch wenn das Promise noch läuft', fakeAsync(() => {
    let ergebnis: unknown;
    withinBudget(new Promise(() => undefined), 5000).then(r => ergebnis = r);

    tick(4999);
    expect(ergebnis).toBeUndefined();
    tick(1);
    expect(ergebnis).toEqual({ done: false });
  }));

  it('Rückstau: das Budget eines hinten eingereihten Zugriffs zählt ab dem Einreihen, nicht ab seinem Start', fakeAsync(() => {
    const storage = tokenStorageFake({ manual: true });
    const store = new SessionStore(storage, () => true, () => session('A'));
    store.save(1);                    // Namensänderung 1, hängt
    store.save(1);                    // Namensänderung 2
    let ergebnis: unknown;
    withinBudget(store.save(1), 5000).then(r => ergebnis = r);   // Refresh-save

    tick(5000);

    expect(ergebnis).toEqual({ done: false });
    expect(storage.calls()).toEqual(['save:A']);
  }));
});
