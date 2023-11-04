package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ShoppingListDtoMapperTest {

    private final ShoppingListDtoMapper shoppingListDtoMapper = new ShoppingListDtoMapper();

    @Test
    void addEditShoppingListDtoToEntity_positive() {
        var entity = shoppingListDtoMapper.addEditShoppingListDtoToEntity(shoppingListDto(), group());

        assertEquals(1L, entity.getId());
        assertEquals("Einkaufsliste", entity.getName());
        assertEquals(10L, entity.getGroup().getId());
    }

   @Test
    void addEditShoppingListDtoToEntity_negative() {
        var entity = shoppingListDtoMapper.addEditShoppingListDtoToEntity(shoppingListDto(), group());

        assertNotEquals(5L, entity.getId());
        assertNotEquals("Shoppinglist", entity.getName());
        assertNotEquals(1L, entity.getGroup().getId());
    }

    private AddEditShoppingListDto shoppingListDto() {
        var shoppingList = new AddEditShoppingListDto();
        shoppingList.setId(1L);
        shoppingList.setName("Einkaufsliste");
        shoppingList.setGroupId(group().getId());
        return shoppingList;
    }

    private Group group() {
        var group = new Group();
        group.setId(10L);
        group.setName("NewGroup");
        return group;
    }
}
