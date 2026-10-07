import { ShoppingitemDto } from "./shoppingitem-dto";

export interface ShoppinglistDto {
      id: number;
      name: string;
      shoppingItems: ShoppingitemDto[];
}
