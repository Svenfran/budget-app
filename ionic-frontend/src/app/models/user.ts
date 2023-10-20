export class UserDto {
    id: number;
    userName: string;
    userEmail: string;

    constructor(id: number, userName: string, userEmail?: string) {
        if (userEmail) {
            this.id = id;
            this.userName = userName;
            this.userEmail = userEmail;
        }
        if (!userEmail) {
            this.id = id;
            this.userName = userName;
        }
    }
}
