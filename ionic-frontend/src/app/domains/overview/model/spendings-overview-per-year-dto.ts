import { SpendingsOverviewUserDto } from "./spendings-overview-user-dto";

export interface SpendingsOverviewPerYearDto {
    year: number;
    sumTotalYear: number;
    spendingsYearlyUser: SpendingsOverviewUserDto[];
    
}