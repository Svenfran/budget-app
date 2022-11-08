package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingItem;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;
import org.springframework.stereotype.Service;

@Service
public class ShoppingItemDtoMapper {

    public ShoppingItem addEditShoppingItemDtoToEntity(AddEditShoppingItemDto dto, ShoppingList shoppingList) {
        var entity = new ShoppingItem();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setCompleted(dto.isCompleted());
        entity.setShoppingList(shoppingList);
        return entity;
    }
}
