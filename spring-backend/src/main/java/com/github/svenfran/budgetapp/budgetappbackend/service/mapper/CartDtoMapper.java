package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.*;
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils;
import org.springframework.stereotype.Service;

@Service
public class CartDtoMapper {

    public Cart cartDtoToEntity(CartDto dto, Category category, User user, Group group) {
        var cart = new Cart();
        cart.setId(dto.getId());
        cart.setTitle(dto.getTitle());
        cart.setDescription(dto.getDescription());
        cart.setAmount(dto.getAmount());
        cart.setDeleted(false);
        cart.setDatePurchased(dto.getDatePurchased() != null ? DateUtils.toDate(dto.getDatePurchased()) : null);
        cart.setGroup(group);
        cart.setCategory(category);
        cart.setUser(user);
        return cart;
    }
}
