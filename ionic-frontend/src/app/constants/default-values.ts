import { Group } from "../model/group";
import { GroupMembers } from '../groupmembers/model/groupmembers-dto';
import { DEFAULT_CURRENCY } from './currency';

export enum INIT_VALUES {
    DEFAULT = "DEFAULT",
    NO_GROUP = "no-group",
    SERVER_UNAVAILABLE = "server-unavailable",
    HEALTH_UP = "UP",
    HEALTH_DOWN = "DOWN"
}

export class Init {
    public static DEFAULT_GROUP: Group = { id: 0, name: '', dateCreated: new Date(), flag: INIT_VALUES.DEFAULT, currencyCode: DEFAULT_CURRENCY };
    public static DEFAULT_GROUP_MEMBERS: GroupMembers = { id: 0, name: '', ownerName: '', ownerId: 0, members: [], flag: INIT_VALUES.DEFAULT };
}

export enum INIT_NUMBERS {
    MAX_LENGTH = 200,
    MAX_LENGTH_50 = 50,
    /** Resync nach App-Resume nur, wenn die App länger als diese Zeit im Hintergrund war (DIV-36, D-001). */
    RESYNC_AFTER_BACKGROUND_MS = 30000,
    /** Höchstens ein Resync pro Intervall (DIV-36). */
    RESYNC_MIN_INTERVAL_MS = 60000,
    /** Health-Abfrage gilt nach dieser Zeit ohne Antwort als DOWN (Server bzw. Netz antwortet nicht). */
    HEALTH_CHECK_TIMEOUT_MS = 10000,
    /** Timeout des HTTP-Aufrufs beim Refresh (DIV-65); danach gilt der Refresh als vorübergehend gescheitert. */
    REFRESH_TIMEOUT_MS = 15000,
    /**
     * Wartebudget der Aufrufer auf den Session-Speicher (DIV-65), gemessen ab dem Einreihen – kein Timeout des nativen
     * Zugriffs selbst. Refresh wartet so lange auf das Speichern, autoLogin auf das Laden.
     */
    STORAGE_WAIT_MS = 5000,
    /** Access Token gilt für Requests nur als gültig, wenn er noch mindestens so lange läuft (DIV-65). */
    ACCESS_TOKEN_MIN_VALIDITY_MS = 30000,
    /** Mindestrestlaufzeit des Access Tokens beim WebSocket-CONNECT (DIV-65). */
    WEBSOCKET_TOKEN_MIN_VALIDITY_MS = 60000
}
