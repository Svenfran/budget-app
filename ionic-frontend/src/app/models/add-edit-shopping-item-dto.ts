export class AddEditShoppingItemDto {
    constructor(
        public id: number,
        public name: string,
        public completed: boolean,
        public shoppingListId: number,
        public groupId: number
    ) {}
}
