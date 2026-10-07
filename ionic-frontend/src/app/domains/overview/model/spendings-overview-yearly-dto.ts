import { SpendingsOverviewPerYearDto } from "./spendings-overview-per-year-dto";
import { SpendingsOverviewTotalYearDto } from "./spendings-overview-total-year-dto";

export interface SpendingsOverviewYearlyDto {
    year: number;
    spendingsTotalYear: SpendingsOverviewTotalYearDto;
    spendingsPerYear: SpendingsOverviewPerYearDto[];
    availableYears: number[];
}