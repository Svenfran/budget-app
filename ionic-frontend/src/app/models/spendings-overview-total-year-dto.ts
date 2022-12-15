import { SpendingsOverviewUserDto } from "./spendings-overview-user-dto";

export class SpendingsOverviewTotalYearDto {
    constructor(
        public sumTotalYear: number,
        public spendingsTotalUser: SpendingsOverviewUserDto[]
    ) {}
}
