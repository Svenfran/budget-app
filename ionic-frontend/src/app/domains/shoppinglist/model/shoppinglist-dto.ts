import { ShoppingitemDto } from "./shoppingitem-dto";

export interface ShoppinglistDto {
      id: number;
      name: string;
      shoppingItems: ShoppingitemDto[];
      /** Revision der Listenreihenfolge der Gruppe; nur in der Antwort des GET (DIV-1). */
      listOrderRevision?: number;
      /** Revision der Reihenfolge der Einträge dieser Liste; nur in der Antwort des GET (DIV-1). */
      itemOrderRevision?: number;
}
