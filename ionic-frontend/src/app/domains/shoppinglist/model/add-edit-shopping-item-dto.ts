export interface AddEditShoppingItemDto {
    id: number | null;
    name: string;
    completed: boolean;
    shoppingListId: number;
    groupId: number;
}
