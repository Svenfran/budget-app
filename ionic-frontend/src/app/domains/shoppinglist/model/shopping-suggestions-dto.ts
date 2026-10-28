/** Einkaufsvorschläge einer Liste (DIV-23). */
export interface ShoppingSuggestionsDto {
    groupId: number;
    shoppingListId: number;
    /** true: die Liste hat noch keine Käufe, `names` sind die Standardvorschläge */
    defaultSuggestions: boolean;
    names: string[];
}
