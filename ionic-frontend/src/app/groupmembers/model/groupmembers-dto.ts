import { UserDto } from "src/app/model/user-dto";

export interface GroupMembers {
    id: number;
    name: string;
    ownerName: string;
    ownerId: number;
    members: UserDto[];
    flag?: string;
}