package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.dao.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dao.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dao.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dao.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto;
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

    @Autowired
    private GroupRepository groupRepository;

    public List<CartDto> getAllCarts() {
        List<Cart> cartList = cartRepository.findAllByOrderByDatePurchasedDesc();
        return cartList.stream().map(CartDto::new).toList();
    }

    public List<CartDto> getCartsByGroupId(Long groupId) {
        List<Cart> cartList = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(groupId);
        return cartList.stream().map(CartDto::new).toList();
    }

    public CartDto getCartById(Long id) throws CartNotFoundException {
        return new CartDto(cartRepository.findById(id)
                .orElseThrow(() -> new CartNotFoundException("Cart with id " + id + " not found")));
    }

    public CartDto addCart(CartDto cartDto) throws AddCartCategoryNotFoundException, UserNotFoundException, GroupNotFoundException {
        var category = categoryRepository.findById(cartDto.getCategoryDto().getId())
                .orElseThrow(() -> new AddCartCategoryNotFoundException("Add Cart: Category with id " + cartDto.getCategoryDto().getId() + " not found"));
        var user = getCurrentUser();
        var group = groupRepository.findById(cartDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Add Cart: Group with id " + cartDto.getGroupId() + " not found"));
        return new CartDto(cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDto, category, user, group)));
    }

    public CartDto updateCart(CartDto cartDto) throws UpdateCartCategoryNotFoundException, UserNotFoundException, GroupNotFoundException {
        var category = categoryRepository.findById(cartDto.getCategoryDto().getId())
                .orElseThrow(() -> new UpdateCartCategoryNotFoundException("Update Cart: Category with id " + cartDto.getCategoryDto().getId() + " not found"));
        var user = getCurrentUser();
        var group = groupRepository.findById(cartDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Update Cart: Group with id " + cartDto.getGroupId() + " not found"));
        return new CartDto(cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDto, category, user, group)));
    }

    public void deleteCart(Long id) {
        cartRepository.deleteById(id);
    }

    // TODO: Derzeit angemeldete Nutzer -> Spring Security
    private User getCurrentUser() throws UserNotFoundException {
        // Sven als Nutzer, id = 1
        var userId = 1L;
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));
    }
}
