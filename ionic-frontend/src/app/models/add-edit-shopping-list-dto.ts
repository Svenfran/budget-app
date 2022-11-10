export class AddEditShoppingListDto {
    constructor(
        public id: number,
        public name: string,
        public groupId: number
    ) {}
}
