package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShoppingListDtoMapperTest {

    private final ShoppingListDtoMapper shoppingListDtoMapper = new ShoppingListDtoMapper();

    @Test
    void addEditShoppingListDtoToEntity_positive() {
        var entity = shoppingListDtoMapper.addEditShoppingListDtoToEntity(shoppingListDto(), group());

        assertEquals(entity.getId(), 1L);
        assertEquals(entity.getName(), "Einkaufsliste");
        assertEquals(entity.getGroup().getId(), 10L);
    }

   @Test
    void addEditShoppingListDtoToEntity_negative() {
        var entity = shoppingListDtoMapper.addEditShoppingListDtoToEntity(shoppingListDto(), group());

        assertNotEquals(entity.getId(), 5L);
        assertNotEquals(entity.getName(), "Shoppinglist");
        assertNotEquals(entity.getGroup().getId(), 1L);
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
