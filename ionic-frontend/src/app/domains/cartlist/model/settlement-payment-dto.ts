import { UserDto } from "src/app/model/user-dto";

export interface SettlementPaymentDto {
    /** Id des Settlements, nur beim Bearbeiten (DIV-74). `member` ist immer der Empfänger. */
    id?: number;
    amount: number;
    groupId: number;
    member: UserDto;
    datePurchased: Date;
}