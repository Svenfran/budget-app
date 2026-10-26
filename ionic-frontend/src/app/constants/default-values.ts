import { Group } from "../model/group";
import { GroupMembers } from '../groupmembers/model/groupmembers-dto';

export enum INIT_VALUES {
    DEFAULT = "DEFAULT",
    NO_GROUP = "no-group",
    SERVER_UNAVAILABLE = "server-unavailable",
    HEALTH_UP = "UP",
    HEALTH_DOWN = "DOWN"
}

export class Init {
    public static DEFAULT_GROUP: Group = { id: 0, name: '', dateCreated: new Date(), flag: INIT_VALUES.DEFAULT };
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
    HEALTH_CHECK_TIMEOUT_MS = 10000
}

export const SETTLEMENTPAYMENT_CATEGORIES = ['Ausgleichszahlung', 'Settlement payment', 'Pago de ajuste'];