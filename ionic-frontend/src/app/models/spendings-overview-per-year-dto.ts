import { SpendingsOverviewUserDto } from "./spendings-overview-user-dto";

export class SpendingsOverviewPerYearDto {
    constructor(
        public year: number,
        public sumTotalYear: number,
        public spendingsYearlyUser: SpendingsOverviewUserDto[]
    ) {}
}
