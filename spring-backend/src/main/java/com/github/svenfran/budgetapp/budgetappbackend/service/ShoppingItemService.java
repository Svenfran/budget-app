package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingListRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.ShoppingItemDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ShoppingItemService {

    @Autowired
    private ShoppingListRepository shoppingListRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShoppingItemDtoMapper shoppingItemDtoMapper;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private ShoppingItemRepository shoppingItemRepository;


    public AddEditShoppingItemDto addShoppingItem(AddEditShoppingItemDto dto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListDoesNotBelongToGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(dto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Add ShoppingItem: Group not found"));
        var shoppingList = shoppingListRepository.findById(dto.getShoppingListId()).
                orElseThrow(() -> new ShoppingListNotFoundException("Add ShoppingItem: Shoppinglist not found"));

        if (group.getOwner().equals(user) || group.getMembers().contains(user)) {
            if (!shoppingList.getGroup().equals(group)) {
                throw new ShoppingListDoesNotBelongToGroupException("Add ShoppingItem: Shoppinglist does not belong to group");
            } else {
                return new AddEditShoppingItemDto(shoppingItemRepository.save(shoppingItemDtoMapper.addEditShoppingItemDtoToEntity(dto, shoppingList)));
            }
        } else throw new NotOwnerOrMemberOfGroupException("Add ShoppingItem: You are neither the owner nor a member of the group");
    }

    public AddEditShoppingItemDto updateShoppingItem(AddEditShoppingItemDto dto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingItemNotFoundException, ShoppingListDoesNotBelongToGroupException, ShoppingItemDoesNotBelongToShoppingListException {
        var user = getCurrentUser();
        var group = groupRepository.findById(dto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Update ShoppingItem: Group not found"));
        var shoppingList = shoppingListRepository.findById(dto.getShoppingListId()).
                orElseThrow(() -> new ShoppingListNotFoundException("Update ShoppingItem: Shoppinglist not found"));
        var shoppingItem = shoppingItemRepository.findById(dto.getId()).
                orElseThrow(() -> new ShoppingItemNotFoundException("Update ShoppingItem: ShoppingItem not found"));

        if (group.getOwner().equals(user) || group.getMembers().contains(user)) {
            if (!shoppingList.getGroup().equals(group)) {
                throw new ShoppingListDoesNotBelongToGroupException("Update ShoppingItem: Shoppinglist does not belong to group");
            } else if (!shoppingList.getId().equals(shoppingItem.getShoppingList().getId())) {
                throw new ShoppingItemDoesNotBelongToShoppingListException("Update ShoppingItem: ShoppingItem does not belong to shoppinglist");
            } else {
                return new AddEditShoppingItemDto(shoppingItemRepository.save(shoppingItemDtoMapper.addEditShoppingItemDtoToEntity(dto, shoppingList)));
            }
        } else throw new NotOwnerOrMemberOfGroupException("Update ShoppingItem: You are neither the owner nor a member of the group");
    }

    public void deleteShoppingItem(AddEditShoppingItemDto dto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingItemNotFoundException, ShoppingListDoesNotBelongToGroupException, ShoppingItemDoesNotBelongToShoppingListException {
        var user = getCurrentUser();
        var group = groupRepository.findById(dto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Delete ShoppingItem: Group not found"));
        var shoppingList = shoppingListRepository.findById(dto.getShoppingListId()).
                orElseThrow(() -> new ShoppingListNotFoundException("Delete ShoppingItem: Shoppinglist not found"));
        var shoppingItem = shoppingItemRepository.findById(dto.getId()).
                orElseThrow(() -> new ShoppingItemNotFoundException("Delete ShoppingItem: ShoppingItem not found"));

        if (group.getOwner().equals(user) || group.getMembers().contains(user)) {
            if (!shoppingList.getGroup().equals(group)) {
                throw new ShoppingListDoesNotBelongToGroupException("Delete ShoppingItem: Shoppinglist does not belong to group");
            } else if (!shoppingList.getId().equals(shoppingItem.getShoppingList().getId())) {
                throw new ShoppingItemDoesNotBelongToShoppingListException("Delete ShoppingItem: ShoppingItem does not belong to shoppinglist");
            } else {
                shoppingItemRepository.deleteById(shoppingItem.getId());
            }
        } else throw new NotOwnerOrMemberOfGroupException("Delete ShoppingItem: You are neither the owner nor a member of the group");
    }

    // TODO: Derzeit angemeldete Nutzer -> Spring Security
    private User getCurrentUser() throws UserNotFoundException {
        // Sven als Nutzer, id = 1
        var userId = UserEnum.CURRENT_USER.getId();
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));
    }
}
