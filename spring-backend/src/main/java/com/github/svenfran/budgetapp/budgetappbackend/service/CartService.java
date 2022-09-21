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
            var category = categoryRepository.findById(cart.getCategory().getId()).get();
            var user = userRepository.findById(cart.getUser().getId()).get();
            var categoryDto = new CategoryDto(category);
            var userDto = new UserDto(user);
            var cartDto = new CartDto(cart, categoryDto, userDto);
            cartDtoList.add(cartDto);
        }
        return cartDtoList;
    }
}
