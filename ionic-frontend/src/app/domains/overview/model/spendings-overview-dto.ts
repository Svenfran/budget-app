import { SpendingsOverviewPerMonthDto } from "./spendings-overview-per-month-dto";
import { SpendingsOverviewTotalYearDto } from "./spendings-overview-total-year-dto";

export interface SpendingsOverviewDto {
    year: number;
    spendingsTotalYear: SpendingsOverviewTotalYearDto;
    spendingsPerMonth: SpendingsOverviewPerMonthDto[] | [];
    availableYears: number[];
}