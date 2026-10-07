export interface PasswordChangeDto {
    userId: number;
    oldPassword: string;
    newPassword: string;
}