import { UserDto } from "./user";

export class ChangeGroupOwner {
    constructor(
        public newOwner: UserDto,
        public groupId: number
    ) {}
}
