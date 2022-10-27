import { UserDto } from "./user";

export class RemoveMemberDto {
    constructor(
        public id: number,
        public name: string,
        public member: UserDto
    ) {} 
}
