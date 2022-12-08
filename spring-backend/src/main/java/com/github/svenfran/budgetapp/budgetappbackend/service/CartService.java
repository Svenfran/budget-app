package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.CartDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CartService {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartDtoMapper cartDtoMapper;


    public List<CartDto> getCartsByGroupId(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(groupId).
                orElseThrow(() -> new GroupNotFoundException("Get Carts: Group not found"));
        var groupOwner = group.getOwner();
        var groupMembers = group.getMembers();

        if (groupOwner.equals(user) || groupMembers.contains(user)) {
            var cartList = cartRepository.findCartsByGroupIdAndIsDeletedFalseOrderByDatePurchasedDesc(groupId);
            return cartList.stream().map(CartDto::new).toList();
        } else throw new NotOwnerOrMemberOfGroupException("Get Carts: You are either a member nor the owner of the group");
    }

    public CartDto getCartById(Long id) throws CartNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = getCurrentUser();
        var cart = cartRepository.findById(id).
                orElseThrow(() -> new CartNotFoundException("Get Cart By Id: Cart not found"));
        var groupOwner = cart.getGroup().getOwner();
        var groupMembers = cart.getGroup().getMembers();

        if (groupOwner.equals(user) || groupMembers.contains(user)) {
            return new CartDto(cart);
        } else throw new NotOwnerOrMemberOfGroupException("Get Cart By Id: You are either a member nor the owner of the group");
    }

    public CartDto addCart(CartDto cartDto) throws AddCartCategoryNotFoundException, UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, GroupIdNotFoundException {
        if (cartDto.getGroupId() == null) {
            throw new GroupIdNotFoundException("Add Cart: Group Id for this cart is null");
        }
        var user = getCurrentUser();
        var category = categoryRepository.findById(cartDto.getCategoryDto().getId())
                .orElseThrow(() -> new AddCartCategoryNotFoundException("Add Cart: Category not found"));
        var group = groupRepository.findById(cartDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Add Cart: Group not found"));
        var groupOwner = group.getOwner();
        var groupMembers = group.getMembers();

        if (groupOwner.equals(user) || groupMembers.contains(user)) {
            return new CartDto(cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDto, category, user, group)));
        } else throw new NotOwnerOrMemberOfGroupException("Add Cart: You are either a member nor the owner of the group");
    }

    public CartDto updateCart(CartDto cartDto) throws UpdateCartCategoryNotFoundException, UserNotFoundException, GroupNotFoundException, CartNotFoundException, NotOwnerOfCartException, NotOwnerOrMemberOfGroupException, GroupIdNotFoundException {
        if (cartDto.getGroupId() == null) {
            throw new GroupIdNotFoundException("Update Cart: Group Id for this cart is null");
        }
        var user = getCurrentUser();
        var cart = cartRepository.findById(cartDto.getId()).
                orElseThrow(() -> new CartNotFoundException("Update Cart: Cart not found"));
        var category = categoryRepository.findById(cartDto.getCategoryDto().getId())
                .orElseThrow(() -> new UpdateCartCategoryNotFoundException("Update Cart: Category not found"));
        var group = groupRepository.findById(cartDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Update Cart: Group not found"));
        var groupOwner = group.getOwner();
        var groupMembers = group.getMembers();
        var cartOwner = cart.getUser();

        if (groupOwner.equals(user) || groupMembers.contains(user)) {
            if (cartOwner.equals(user)) {
                return new CartDto(cartRepository.save(cartDtoMapper.CartDtoToEntity(cartDto, category, user, group)));
            } else throw new NotOwnerOfCartException("Update Cart: You are not the owner of the cart");
        } else throw new NotOwnerOrMemberOfGroupException("Update Cart: You are either a member nor the owner of the group");
    }

    public void deleteCart(Long id) throws UserNotFoundException, CartNotFoundException, GroupNotFoundException, NotOwnerOfCartException, NotOwnerOrMemberOfGroupException {
        var user = getCurrentUser();
        var cart = cartRepository.findById(id).
                orElseThrow(() -> new CartNotFoundException("Delete Cart: Cart not found"));
        var group = groupRepository.findById(cart.getGroup().getId()).
                orElseThrow(() -> new GroupNotFoundException("Delete Cart: Group not found"));
        var groupOwner = group.getOwner();
        var groupMembers = group.getMembers();
        var cartOwner = cart.getUser();

        if (groupOwner.equals(user) || groupMembers.contains(user)) {
            if (cartOwner.equals(user)) {
                cartRepository.deleteById(id);
            } else throw new NotOwnerOfCartException("Delete Cart: You are not the owner of the cart");
        } else throw new NotOwnerOrMemberOfGroupException("Delete Cart: You are either a member nor the owner of the group");
    }

    // TODO: Derzeit angemeldete Nutzer -> Spring Security
    private User getCurrentUser() throws UserNotFoundException {
        // Sven als Nutzer, id = 1
        var userId = UserEnum.CURRENT_USER.getId();
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));
    }
}
