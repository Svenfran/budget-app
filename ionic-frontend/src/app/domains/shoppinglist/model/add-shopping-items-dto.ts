/** Mehrere Einträge auf einmal anlegen, z. B. ausgewählte Vorschläge (DIV-23). */
export interface AddShoppingItemsDto {
    groupId: number;
    shoppingListId: number;
    names: string[];
}
