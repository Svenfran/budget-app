import { InjectionToken } from '@angular/core';
import { Device } from '@capacitor/device';

/**
 * Ermittelt die DeviceId. Schlägt das fehl, wird '' geliefert: das Backend lehnt Login/Registrierung dann mit
 * 'DeviceId is required' ab, statt dass Requests auf ein abgelehntes Promise warten und scheitern.
 */
export function loadDeviceId(getId: () => Promise<{ identifier: string }>): Promise<string> {
  return getId().then(device => device.identifier).catch(() => '');
}

/**
 * DeviceId des Geräts, einmal pro App-Start ermittelt (DIV-46). Login, Registrierung und authentifizierte Requests
 * warten auf dieses Promise, statt eine noch leere DeviceId zu senden. Als Token, damit Tests ein steuerbares Promise
 * einsetzen können (der Capacitor-Proxy `Device` lässt sich nicht zuverlässig per spyOn ersetzen).
 */
export const DEVICE_ID = new InjectionToken<Promise<string>>('DEVICE_ID', {
  providedIn: 'root',
  factory: () => loadDeviceId(() => Device.getId()),
});
