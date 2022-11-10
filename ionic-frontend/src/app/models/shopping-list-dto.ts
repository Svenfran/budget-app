import { ShoppingItemDto } from "./shopping-item-dto";

export class ShoppingListDto {
    constructor(
        public id: number,
        public name: string,
        public shoppingItems: ShoppingItemDto[]
    ) {}
}
