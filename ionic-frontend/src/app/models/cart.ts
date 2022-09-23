import { CategoryDto } from "./category";
import { UserDto } from "./user";

export class Cart {
    constructor(
        public id: number,
        public title: string,
        public description: string,
        public amount: number,
        public datePurchased: Date,
        public userDto: UserDto,
        public categoryDto: CategoryDto
    ) {}
}
