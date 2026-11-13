import { UserDto } from "src/app/model/user-dto";

export interface SettlementPaymentDto {
    amount: number;
    groupId: number;
    member: UserDto;
    datePurchased: Date;
}