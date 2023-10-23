export class PasswordChangeDto {
    constructor(
        public userId: number,
        public oldPassword: string,
        public newPassword: string
    ) {}
}
