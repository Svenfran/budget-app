import { SpendingsOverviewUserDto } from "./spendings-overview-user-dto";

export class SpendingsOverviewPerMonthDto {
    constructor(
        public month: number,
        public monthName: string,
        public sumTotalMonth: number,
        public spendingsMonthlyUser: SpendingsOverviewUserDto[]
    ) {}
}
