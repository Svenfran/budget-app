import { loadDeviceId } from './device-id';

describe('loadDeviceId', () => {
  it('liefert die Kennung des Geräts', async () => {
    expect(await loadDeviceId(() => Promise.resolve({ identifier: 'device-1' }))).toBe('device-1');
  });

  it('liefert eine leere DeviceId, wenn die Ermittlung fehlschlägt', async () => {
    expect(await loadDeviceId(() => Promise.reject(new Error('nicht verfügbar')))).toBe('');
  });
});
