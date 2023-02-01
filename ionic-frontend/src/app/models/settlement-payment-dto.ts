import { UserDto } from "./user";

export class SettlementPaymentDto {
    constructor(
        public amount: number,
        public groupId: number,
        public member: UserDto
    ){}
}
