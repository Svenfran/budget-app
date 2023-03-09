package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingItem;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ShoppingItemDtoMapperTest {

    private final ShoppingItemDtoMapper shoppingItemDtoMapper = new ShoppingItemDtoMapper();

    @Test
    void addEditShoppingItemDtoToEntity_positive() {
        var entity = shoppingItemDtoMapper.addEditShoppingItemDtoToEntity(shoppingItemDto(), shoppingList());

        assertEquals(entity.getId(), 2L);
        assertEquals(entity.getName(), "Milk");
        assertEquals(entity.isCompleted(), false);
        assertEquals(entity.getShoppingList().getId(), 1L);
        assertEquals(entity.getShoppingList().getName(), "Einkaufsliste");
        assertEquals(entity.getShoppingList().getGroup().getId(), 10L);
    }

    @Test
    void addEditShoppingItemDtoToEntity_negative() {
        var entity = shoppingItemDtoMapper.addEditShoppingItemDtoToEntity(shoppingItemDto(), shoppingList());

        assertNotEquals(entity.getId(), 22L);
        assertNotEquals(entity.getName(), "Honey");
        assertNotEquals(entity.isCompleted(), true);
        assertNotEquals(entity.getShoppingList().getId(), 10L);
        assertNotEquals(entity.getShoppingList().getName(), "Shoppinglist");
        assertNotEquals(entity.getShoppingList().getGroup().getId(), 1L);
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
