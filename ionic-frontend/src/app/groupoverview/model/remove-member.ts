import { UserDto } from "src/app/model/user-dto";

export interface RemoveMemberDto {
    id: number;
    name: string;
    member: UserDto;
}