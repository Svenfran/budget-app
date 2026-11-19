export interface CategoryDto {
    id?: number;
    name: string;
    groupId: number;
    /** Settlement-Kategorie der Gruppe (DIV-74); nur vom Backend gesetzt. */
    settlement?: boolean;
}
