package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import org.springframework.stereotype.Service;

@Service
public class CartDtoMapper {

    public Cart CartDtoToEntity(CartDto dto, Category category, User user) {
        var cart = new Cart();
        cart.setId(dto.getId());
        cart.setTitle(dto.getTitle());
        cart.setDescription(dto.getDescription());
        cart.setAmount(dto.getAmount());
        cart.setDatePurchased(dto.getDatePurchased());
        cart.setCategory(category);
        cart.setUser(user);
        return cart;
    }
}
