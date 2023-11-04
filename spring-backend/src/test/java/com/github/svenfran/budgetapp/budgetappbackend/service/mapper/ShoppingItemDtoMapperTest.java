package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShoppingItemDtoMapperTest {

    private final ShoppingItemDtoMapper shoppingItemDtoMapper = new ShoppingItemDtoMapper();

    @Test
    void addEditShoppingItemDtoToEntity_positive() {
        var entity = shoppingItemDtoMapper.addEditShoppingItemDtoToEntity(shoppingItemDto(), shoppingList());

        assertEquals(2L, entity.getId());
        assertEquals("Milk", entity.getName());
        assertFalse(entity.isCompleted());
        assertEquals(1L, entity.getShoppingList().getId());
        assertEquals("Einkaufsliste", entity.getShoppingList().getName());
        assertEquals(10L, entity.getShoppingList().getGroup().getId());
    }

    @Test
    void addEditShoppingItemDtoToEntity_negative() {
        var entity = shoppingItemDtoMapper.addEditShoppingItemDtoToEntity(shoppingItemDto(), shoppingList());

        assertNotEquals(22L, entity.getId());
        assertNotEquals("Honey", entity.getName());
        assertNotEquals(true, entity.isCompleted());
        assertNotEquals(10L, entity.getShoppingList().getId());
        assertNotEquals("Shoppinglist", entity.getShoppingList().getName());
        assertNotEquals(1L, entity.getShoppingList().getGroup().getId());
    }

    private ShoppingList shoppingList() {
        var shoppingList = new ShoppingList();
        shoppingList.setId(1L);
        shoppingList.setName("Einkaufsliste");
        shoppingList.setGroup(group());
        return shoppingList;
    }

    private AddEditShoppingItemDto shoppingItemDto() {
        var shoppingItem = new AddEditShoppingItemDto();
        shoppingItem.setId(2L);
        shoppingItem.setName("Milk");
        shoppingItem.setCompleted(false);
        shoppingItem.setGroupId(group().getId());
        return shoppingItem;
    }

    private Group group() {
        var group = new Group();
        group.setId(10L);
        group.setName("NewGroup");
        return group;
    }
}
