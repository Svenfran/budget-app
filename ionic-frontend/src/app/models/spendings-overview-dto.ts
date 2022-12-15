import { SpendingsOverviewPerMonthDto } from "./spendings-overview-per-month-dto";
import { SpendingsOverviewTotalYearDto } from "./spendings-overview-total-year-dto";

export class SpendingsOverviewDto {
    constructor(
        public year: number,
        public spendingsTotalYear: SpendingsOverviewTotalYearDto,
        public spendingsPerMonth: SpendingsOverviewPerMonthDto[],
        public availableYears: number[]
    ) {}
}
