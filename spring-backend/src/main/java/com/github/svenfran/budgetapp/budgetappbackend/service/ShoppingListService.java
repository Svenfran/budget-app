package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingListRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.ShoppingListDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShoppingListService {

    @Autowired
    private ShoppingListRepository shoppingListRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShoppingListDtoMapper shoppingListDtoMapper;

    @Autowired
    private ShoppingItemRepository shoppingItemRepository;

    public List<ShoppingListDto> getShoppingListsForGroup(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(groupId).
                orElseThrow(() -> new GroupNotFoundException("Get Shoppinglist: Group not found"));

        if (group.getOwner().equals(user) || group.getMembers().contains(user)) {
            return shoppingListRepository.findAllByGroup_IdOrderById(groupId).
                    stream().map(ShoppingListDto::new).toList();
        } else throw new NotOwnerOrMemberOfGroupException("Get Shoppinglist: You are either the owner nor a member of the group");
    }

    public AddEditShoppingListDto addShoppingList(AddEditShoppingListDto addEditShoppingListDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(addEditShoppingListDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Add Shoppinglist: Group not found"));

        if (group.getOwner().equals(user) || group.getMembers().contains(user)) {
            return new AddEditShoppingListDto(shoppingListRepository.save(shoppingListDtoMapper.addEditShoppingListDtoToEntity(addEditShoppingListDto, group)));
        } else throw new NotOwnerOrMemberOfGroupException("Add Shoppinglist: You are either the owner nor a member of the group");
    }

    public AddEditShoppingListDto updateShoppingList(AddEditShoppingListDto addEditShoppingListDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListNotFoundException, ShoppingListDoesNotBelongToGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(addEditShoppingListDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Update Shoppinglist: Group not found"));
        var shoppingList = shoppingListRepository.findById(addEditShoppingListDto.getId()).
                orElseThrow(() -> new ShoppingListNotFoundException("Update Shoppinglist: Shoppinglist not found"));

        if (group.getOwner().equals(user) || group.getMembers().contains(user)) {
            if (shoppingList.getGroup().equals(group)) {
                return new AddEditShoppingListDto(shoppingListRepository.save(shoppingListDtoMapper.addEditShoppingListDtoToEntity(addEditShoppingListDto, group)));
            } else throw new ShoppingListDoesNotBelongToGroupException("Update Shoppinglist: Shoppinglist does not belong to group");
        } else throw new NotOwnerOrMemberOfGroupException("Update Shoppinglist: You are either the owner nor a member of the group");
    }

    public void deleteShoppingList(AddEditShoppingListDto dto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListNotFoundException, ShoppingListDoesNotBelongToGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(dto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Delete Shoppinglist: Group not found"));
        var shoppingList = shoppingListRepository.findById(dto.getId()).
                orElseThrow(() -> new ShoppingListNotFoundException("Delete Shoppinglist: Shoppinglist not found"));

        if (group.getOwner().equals(user) || group.getMembers().contains(user)) {
            if (shoppingList.getGroup().equals(group)) {
                shoppingItemRepository.deleteAll(shoppingList.getShoppingItems());
                shoppingListRepository.deleteById(shoppingList.getId());
            } else throw new ShoppingListDoesNotBelongToGroupException("Delete Shoppinglist: Shoppinglist does not belong to group");
        } else throw new NotOwnerOrMemberOfGroupException("Delete Shoppinglist: You are either the owner nor a member of the group");
    }

    // TODO: Derzeit angemeldete Nutzer -> Spring Security
    private User getCurrentUser() throws UserNotFoundException {
        // Sven als Nutzer, id = 1
        var userId = UserEnum.CURRENT_USER.getId();
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));
    }

}
