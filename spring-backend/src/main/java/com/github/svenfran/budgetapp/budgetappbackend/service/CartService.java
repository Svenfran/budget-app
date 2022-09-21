package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dao.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dao.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dao.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    public List<CartDto> getAllCarts() {
        List<Cart> cartList = cartRepository.findAll();
        List<CartDto> cartDtoList = new ArrayList<>();
        for (Cart cart : cartList) {
            var category = categoryRepository.findById(cart.getCategory().getId())
                    .orElseThrow(() -> new RuntimeException("Category not found!"));
            var user = userRepository.findById(cart.getUser().getId())
                    .orElseThrow(() -> new RuntimeException("User not found!"));
            var categoryDto = new CategoryDto(category);
            var userDto = new UserDto(user);
            var cartDto = new CartDto(cart, categoryDto, userDto);
            cartDtoList.add(cartDto);
        }
        return cartDtoList;
    }

    public CartDto getCartById(Long id) {
        var cart = cartRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cart not found!"));
        var categoryDto = new CategoryDto(categoryRepository.findById(cart.getCategory().getId())
                .orElseThrow(() -> new RuntimeException("Category  not found!")));
        var userDto = new UserDto(userRepository.findById(cart.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User  not found!")));
        return new CartDto(cart, categoryDto, userDto);
    }
}
