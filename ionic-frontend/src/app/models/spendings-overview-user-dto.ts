export class SpendingsOverviewUserDto {
    constructor(
        public userId: number,
        public userName: string,
        public sum: number,
        public diff: number
    ) {}
}
