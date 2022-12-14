import { UserDto } from "./user";

export class Group {
    constructor(
        public id: number,
        public name: string,
        public dateCreated: Date
    ) {}
}
