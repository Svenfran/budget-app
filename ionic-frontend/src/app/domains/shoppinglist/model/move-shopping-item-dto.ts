export interface MoveShoppingItemDto {
    groupId: number;
    shoppingItemId: number;
    sourceShoppingListId: number;
    targetShoppingListId: number;
    targetShoppingItemIds: number[];
}
