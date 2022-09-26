package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dao.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dao.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dao.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
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
        List<Cart> cartList = cartRepository.findAllByOrderByDatePurchasedDesc();
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

    public CartDto addCart(CartDto cartDto) {
        var cart = new Cart();
        cart.setTitle(cartDto.getTitle());
        cart.setDescription(cartDto.getDescription());
        cart.setAmount(cartDto.getAmount());
        cart.setDatePurchased(cartDto.getDatePurchased());
        cart.setCategory(categoryRepository.findById(cartDto.getCategoryDto().getId()).get());
        // Sven als Nutzer, id = 1
        cart.setUser(getCurrentUser(1L));
        return new CartDto(cartRepository.save(cart));
    }

    public CartDto updateCart(CartDto cartDto) {
        var cart = new Cart();
        cart.setId(cartDto.getId());
        cart.setTitle(cartDto.getTitle());
        cart.setDescription(cartDto.getDescription());
        cart.setAmount(cartDto.getAmount());
        cart.setDatePurchased(cartDto.getDatePurchased());
        cart.setCategory(categoryRepository.findById(cartDto.getCategoryDto().getId()).get());
        // Sven als Nutzer, id = 1
        cart.setUser(getCurrentUser(1L));
        return new CartDto(cartRepository.save(cart));

    }

    public void deleteCart(Long id) {
        cartRepository.deleteById(id);
    }

    // Derzeit angemeldeter User -> Spring Security
    private User getCurrentUser(Long id) {
        return userRepository.findById(id).
                orElseThrow(() -> new RuntimeException("User with id " + id + " not found"));
    }
}
