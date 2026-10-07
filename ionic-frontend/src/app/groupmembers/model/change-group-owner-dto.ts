import { UserDto } from "src/app/model/user-dto";

export interface ChangeGroupOwnerDto {
  newOwner: UserDto;
  groupId: number;
}