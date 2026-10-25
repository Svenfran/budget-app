/**
 * Drosselt den stillen Resync nach Reconnect/App-Resume (DIV-36): höchstens ein Durchlauf pro `minIntervalMs`.
 * Anforderungen innerhalb des Fensters werden zu genau einem Durchlauf am Fensterende zusammengefasst, nicht verworfen.
 * Ohne Verbindung (`canRun() === false`) passiert nichts – kein Timer, keine Requests; der nächste Reconnect fordert
 * ohnehin neu an. Kein periodisches Neuladen.
 */
export class ResyncThrottle {
  private lastRun: number | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private run: () => void,
    private canRun: () => boolean,
    private minIntervalMs: number,
    private now: () => number = () => Date.now(),
  ) {}

  request(): void {
    if (!this.canRun() || this.timer !== null) {
      return;
    }
    const elapsed = this.lastRun === null ? Infinity : this.now() - this.lastRun;
    if (elapsed >= this.minIntervalMs) {
      this.execute();
      return;
    }
    this.timer = setTimeout(() => {
      this.timer = null;
      if (this.canRun()) {
        this.execute();
      }
    }, this.minIntervalMs - elapsed);
  }

  /** Verwirft einen ausstehenden Durchlauf und das Intervall (Logout, Nutzerwechsel, Zerstören der App). */
  reset(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.lastRun = null;
  }

  private execute(): void {
    this.lastRun = this.now();
    this.run();
  }
}

/** War die App zwischen `pausedAt` und `resumedAt` länger als `thresholdMs` im Hintergrund? Ohne Pause-Zeitpunkt: nein. */
export function isLongBackground(pausedAt: number | null, resumedAt: number, thresholdMs: number): boolean {
  return pausedAt !== null && resumedAt - pausedAt > thresholdMs;
}
