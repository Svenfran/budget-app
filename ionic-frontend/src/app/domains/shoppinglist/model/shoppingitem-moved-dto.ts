import { ShoppingitemOrderDto } from './shoppingitem-order-dto';

/** Antwort und WebSocket-Event `move-item` (DIV-2): verschobener Eintrag und beide Reihenfolgen, ohne Eintragsdaten. */
export interface ShoppingitemMovedDto {
    groupId: number;
    shoppingItemId: number;
    source: ShoppingitemOrderDto;
    target: ShoppingitemOrderDto;
}
