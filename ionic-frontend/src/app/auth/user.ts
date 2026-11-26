/**
 * Angemeldeter Nutzer (Profildaten der Session). Seit DIV-65 ohne Token: Der Access Token liegt nur im AuthService
 * (Arbeitsspeicher) und wird nicht über `AuthService.user` verteilt, damit ein Refresh keine Neuladungen auslöst.
 */
export class User {
  constructor(
    public id: number,
    public name: string,
    public email: string,
    /** Absolute Höchstdauer der Session (ms seit Epoch); maßgeblich ist der Server. */
    public sessionExpiresAt: number,
  ) {}
}
