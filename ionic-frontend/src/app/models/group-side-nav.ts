import { UserDto } from "./user";

export class GroupSideNav {
    public id: number;
    public name: string;
    public dateCreated: Date;
    public userDto?: UserDto; // Optional

    constructor(id: number, name: string, dateCreated: Date);
    constructor(id: number, name: string, dateCreated: Date, userDto: UserDto);
    
    constructor(id: number, name: string, dateCreated: Date, userDto?: UserDto) {
        this.id = id;
        this.name = name;
        this.dateCreated = dateCreated;
        if (userDto) {
            this.userDto = userDto;
        }
    }
}
