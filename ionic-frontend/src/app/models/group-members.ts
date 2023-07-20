import { UserDto } from "./user";

export class GroupMembers {
    constructor(
        public id: number,
        public name: string,
        public ownerName: string,
        public ownerId: number,
        public members: UserDto[]
    ) {}
}
