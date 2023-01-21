import { SpendingsOverviewPerYearDto } from "./spendings-overview-per-year-dto";
import { SpendingsOverviewTotalYearDto } from "./spendings-overview-total-year-dto";

export class SpendingsOverviewYearlyDto {
    constructor(
        public year: number,
        public spendingsTotalYear: SpendingsOverviewTotalYearDto,
        public spendingsPerYear: SpendingsOverviewPerYearDto[],
        public availableYears: number[]
    ) {}
}
