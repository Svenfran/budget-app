package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dao.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    @Autowired
    private CartRepository cartRepository;

    public List<CartDto> getAllCarts() {
        List<Cart> cartList = cartRepository.findAll();
        List<CartDto> cartDtoList = new ArrayList<>();
        for (Cart cart : cartList) {
            cartDtoList.add(new CartDto(cart));
        }
        return cartDtoList;
    }

    public CartDto getCartById(Long id) {
        return new CartDto(cartRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cart with id " + id + " not found")));
    }
}
