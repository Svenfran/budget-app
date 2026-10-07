import { SpendingsOverviewUserDto } from "./spendings-overview-user-dto";

export interface SpendingsOverviewPerMonthDto {
    month: number;
    monthName: string;
    sumTotalMonth: number;
    spendingsMonthlyUser: SpendingsOverviewUserDto[] | [];
}