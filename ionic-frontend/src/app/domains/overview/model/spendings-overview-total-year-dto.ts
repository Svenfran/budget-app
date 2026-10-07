import { SpendingsOverviewUserDto } from "./spendings-overview-user-dto";

export interface SpendingsOverviewTotalYearDto {
    sumTotalYear: number;
    spendingsTotalUser: SpendingsOverviewUserDto[] | [];
}