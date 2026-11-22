import { UserDto } from "src/app/model/user-dto";

export interface SettlementPaymentDto {
    /** Id des Settlements, nur beim Bearbeiten (DIV-74). `member` ist immer der Empfänger. */
    id?: number;
    /** Zahler beim Anlegen einer erhaltenen Zahlung (DIV-74); ohne Angabe der angemeldete Nutzer. */
    payer?: UserDto;
    amount: number;
    groupId: number;
    member: UserDto;
    /** ISO-Kalendertag `YYYY-MM-DD` (DIV-81). */
    datePurchased: string;
}