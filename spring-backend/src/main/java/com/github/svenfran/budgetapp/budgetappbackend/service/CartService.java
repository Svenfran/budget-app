package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.AddCartCategoryNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.CartNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.UpdateCartCategoryNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.dao.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dao.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dao.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.CartDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CartService {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartDtoMapper cartDtoMapper;

    public List<CartDto> getAllCarts() {
        List<Cart> cartList = cartRepository.findAllByOrderByDatePurchasedDesc();
        return cartList.stream().map(CartDto::new).toList();
    }

    public CartDto getCartById(Long id) throws CartNotFoundException {
        return new CartDto(cartRepository.findById(id)
                .orElseThrow(() -> new CartNotFoundException("Cart with id " + id + " not found")));
    }

    public CartDto addCart(CartDto cartDto) throws AddCartCategoryNotFoundException {
        var category = categoryRepository.findById(cartDto.getCategoryDto().getId())
                .orElseThrow(() -> new AddCartCategoryNotFoundException("Add Cart: Category with id " + cartDto.getCategoryDto().getId() + " not found"));
        // Sven als Nutzer, id = 1
        var user = getCurrentUser(1L);
        return new CartDto(cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDto, category, user)));
    }

    public CartDto updateCart(CartDto cartDto) throws UpdateCartCategoryNotFoundException {
        var category = categoryRepository.findById(cartDto.getCategoryDto().getId())
                .orElseThrow(() -> new UpdateCartCategoryNotFoundException("Update Cart: Category with id " + cartDto.getCategoryDto().getId() + " not found"));
        // Sven als Nutzer, id = 1
        var user = getCurrentUser(1L);
        return new CartDto(cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDto, category, user)));
    }

    public void deleteCart(Long id) {
        cartRepository.deleteById(id);
    }

    // Derzeit angemeldete Nutzer -> Spring Security
    private User getCurrentUser(Long id) {
        return userRepository.findById(id).
                orElseThrow(() -> new RuntimeException("User with id " + id + " not found"));
    }
}
