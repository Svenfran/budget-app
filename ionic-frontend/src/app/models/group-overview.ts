export class GroupOverview {
    constructor(
        public id: number,
        public name: string,
        public dateCreated: Date,
        public ownerName: string,
        public memberCount: number
    ) {}
}
