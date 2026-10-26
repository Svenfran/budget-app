import { REFERENCE_DATE } from 'src/testing/test-data';
import { isLongBackground, ResyncThrottle } from './resync-throttle';

describe('ResyncThrottle', () => {
  const INTERVAL = 60000;
  let run: jasmine.Spy;
  let connected: boolean;
  let throttle: ResyncThrottle;

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(REFERENCE_DATE);
    run = jasmine.createSpy('run');
    connected = true;
    throttle = new ResyncThrottle(run, () => connected, INTERVAL);
  });

  afterEach(() => jasmine.clock().uninstall());

  it('läuft bei der ersten Anforderung sofort', () => {
    throttle.request();

    expect(run).toHaveBeenCalledTimes(1);
  });

  it('holt eine Anforderung innerhalb des Fensters genau einmal am Fensterende nach', () => {
    throttle.request();
    jasmine.clock().tick(10000);

    throttle.request();
    expect(run).withContext('kein sofortiger zweiter Lauf').toHaveBeenCalledTimes(1);

    jasmine.clock().tick(INTERVAL - 10000 - 1);
    expect(run).toHaveBeenCalledTimes(1);
    jasmine.clock().tick(1);
    expect(run).toHaveBeenCalledTimes(2);
  });

  it('fasst mehrere Anforderungen im Fenster (z. B. Reconnect + Resume) zu genau einem nachgeholten Lauf zusammen', () => {
    throttle.request();
    jasmine.clock().tick(5000);
    throttle.request();
    throttle.request();
    jasmine.clock().tick(20000);
    throttle.request();

    jasmine.clock().tick(INTERVAL);

    expect(run).toHaveBeenCalledTimes(2);
  });

  it('läuft nach Ablauf des Fensters wieder sofort', () => {
    throttle.request();
    jasmine.clock().tick(INTERVAL);

    throttle.request();

    expect(run).toHaveBeenCalledTimes(2);
  });

  it('reset verwirft einen ausstehenden Lauf und das Fenster', () => {
    throttle.request();
    jasmine.clock().tick(1000);
    throttle.request();

    throttle.reset();
    jasmine.clock().tick(INTERVAL);
    expect(run).toHaveBeenCalledTimes(1);

    throttle.request();
    expect(run).withContext('nach reset sofort').toHaveBeenCalledTimes(2);
  });

  it('ohne Verbindung: kein Lauf und kein Timer; mit Verbindung danach sofort', () => {
    connected = false;
    throttle.request();
    jasmine.clock().tick(INTERVAL * 2);
    expect(run).not.toHaveBeenCalled();

    connected = true;
    throttle.request();

    expect(run).toHaveBeenCalledTimes(1);
  });

  it('verwirft den nachgeholten Lauf, wenn bei Fensterende keine Verbindung besteht; die nächste Anforderung läuft', () => {
    throttle.request();
    jasmine.clock().tick(1000);
    throttle.request();

    connected = false;
    jasmine.clock().tick(INTERVAL);
    expect(run).toHaveBeenCalledTimes(1);

    connected = true;
    throttle.request();
    expect(run).toHaveBeenCalledTimes(2);
  });
});

describe('isLongBackground', () => {
  const THRESHOLD = 30000;
  const pausedAt = REFERENCE_DATE.getTime();

  it('gilt erst bei mehr als 30 s im Hintergrund', () => {
    expect(isLongBackground(pausedAt, pausedAt + 29999, THRESHOLD)).toBeFalse();
    expect(isLongBackground(pausedAt, pausedAt + 30000, THRESHOLD)).withContext('genau 30 s: nicht länger').toBeFalse();
    expect(isLongBackground(pausedAt, pausedAt + 30001, THRESHOLD)).toBeTrue();
  });

  it('ohne Pause-Zeitpunkt nie', () => {
    expect(isLongBackground(null, pausedAt + 60000, THRESHOLD)).toBeFalse();
  });
});
