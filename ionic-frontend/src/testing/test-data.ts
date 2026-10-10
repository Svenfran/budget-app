import { RECURRENCE_TYPE } from 'src/app/constants/recurrence-type';
import { Cart } from 'src/app/domains/cartlist/model/cart';
import { CategoryDto } from 'src/app/model/category-dto';
import { Group } from 'src/app/model/group';
import { UserDto } from 'src/app/model/user-dto';

// Festes Datum für alle Testdaten – Tests hängen nie von der aktuellen Uhrzeit ab.
export const REFERENCE_DATE = new Date('2025-06-15T00:00:00Z');

let sequence = 0;
const nextId = () => ++sequence;

export function aUser(overrides: Partial<UserDto> = {}): UserDto {
  const id = nextId();
  return { id, userName: `Nutzer ${id}`, userEmail: `nutzer${id}@example.com`, ...overrides };
}

export function aGroup(overrides: Partial<Group> = {}): Group {
  const id = nextId();
  return { id, name: `Gruppe ${id}`, dateCreated: REFERENCE_DATE, ...overrides };
}

export function aCategory(overrides: Partial<CategoryDto> = {}): CategoryDto {
  return { id: nextId(), name: 'Lebensmittel', groupId: 1, ...overrides };
}

export function aCart(overrides: Partial<Cart> = {}): Cart {
  return {
    id: nextId(),
    title: 'Einkauf',
    description: 'Wocheneinkauf',
    amount: 40,
    datePurchased: REFERENCE_DATE,
    groupId: 1,
    userDto: aUser(),
    categoryDto: aCategory(),
    recurrenceType: RECURRENCE_TYPE.NONE,
    ...overrides,
  };
}
