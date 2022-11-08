package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;
import org.springframework.stereotype.Service;

@Service
public class ShoppingListDtoMapper {

    public ShoppingList addEditShoppingListDtoToEntity(AddEditShoppingListDto dto, Group group) {
        var entity = new ShoppingList();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setGroup(group);
        return entity;
    }
}
