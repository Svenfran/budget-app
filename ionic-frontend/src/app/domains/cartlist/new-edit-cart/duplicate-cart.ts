import { Cart } from '../model/cart';

/** Formularwerte, die beim Duplizieren aus der Vorlage übernommen werden (DIV-73). */
export interface DuplicateFormValues {
  title: string;
  amount: string;
  description: string;
  categoryId: number | undefined;
}

/**
 * Übernimmt Titel, Betrag, Beschreibung und Kategorie der Vorlage. Datum (heute) und Wiederholung („Keine“) setzt die
 * Neuanlage selbst; ID, Template und Metadaten des Originals werden bewusst nicht übernommen.
 */
export function duplicateFormValues(cart: Cart): DuplicateFormValues {
  return {
    title: cart.title,
    amount: (+cart.amount).toFixed(2),
    description: cart.description ?? '',
    categoryId: cart.categoryDto?.id,
  };
}

/**
 * Vorlage für „Duplizieren“: nur ein eigener, regulärer Cart der aktiven Gruppe aus der geladenen Liste. Sonst
 * `undefined` – das Formular öffnet dann als leere Neuanlage (veraltete Liste, manipulierte URL). Eigentum wird wie bei
 * den Sliding-Aktionen über den Nutzernamen bestimmt.
 */
export function duplicateSource(
  cartList: Cart[],
  cartId: number,
  groupId: number,
  userName: string | undefined,
): Cart | undefined {
  const cart = cartList.find(c => c.id === cartId);
  if (!cart || !userName) return undefined;
  if (cart.groupId !== groupId || cart.userDto?.userName !== userName || cart.deleted) return undefined;
  // Ausgleichszahlungen (DIV-74: an der Verknüpfung erkannt) werden nur über das Settlement-Sheet angelegt
  if (cart.settlementId) return undefined;
  return cart;
}
