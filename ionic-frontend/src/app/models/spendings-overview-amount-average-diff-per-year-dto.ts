export class SpendingsOverviewAmountAverageDiffPerYearDto {
    constructor(
        public sumAmount: number,
        public sumAveragePerMember: number,
        public diff: number,
        public userId: number,
        public year: number
    ) {}
}
