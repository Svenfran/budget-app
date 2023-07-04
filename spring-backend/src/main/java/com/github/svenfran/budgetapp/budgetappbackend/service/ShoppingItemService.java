package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingItem;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.ShoppingItemDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class ShoppingItemService {

    @Autowired
    private ShoppingItemDtoMapper shoppingItemDtoMapper;

    @Autowired
    private ShoppingItemRepository shoppingItemRepository;

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private GroupRepository groupRepository;

    @Transactional
    public AddEditShoppingItemDto addShoppingItem(AddEditShoppingItemDto dto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListDoesNotBelongToGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(dto.getShoppingListId());
        verifyShoppingListIsPartOfGroup(shoppingList, group);
        group.setLastUpdateShoppingList(new Date());
        groupRepository.save(group);
        return new AddEditShoppingItemDto(shoppingItemRepository.save(shoppingItemDtoMapper.addEditShoppingItemDtoToEntity(dto, shoppingList)));
    }

    @Transactional
    public AddEditShoppingItemDto updateShoppingItem(AddEditShoppingItemDto dto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingItemNotFoundException, ShoppingListDoesNotBelongToGroupException, ShoppingItemDoesNotBelongToShoppingListException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(dto.getShoppingListId());
        verifyShoppingListIsPartOfGroup(shoppingList, group);
        var shoppingItem = dataLoaderService.loadShoppingItem(dto.getId());
        verifyShoppingItemIsPartOfShoppingList(shoppingList, shoppingItem);
        group.setLastUpdateShoppingList(new Date());
        groupRepository.save(group);
        return new AddEditShoppingItemDto(shoppingItemRepository.save(shoppingItemDtoMapper.addEditShoppingItemDtoToEntity(dto, shoppingList)));
    }

    @Transactional
    public void deleteShoppingItem(AddEditShoppingItemDto dto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingItemNotFoundException, ShoppingListDoesNotBelongToGroupException, ShoppingItemDoesNotBelongToShoppingListException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(dto.getShoppingListId());
        verifyShoppingListIsPartOfGroup(shoppingList, group);
        var shoppingItem = dataLoaderService.loadShoppingItem(dto.getId());
        verifyShoppingItemIsPartOfShoppingList(shoppingList, shoppingItem);
        group.setLastUpdateShoppingList(new Date());
        groupRepository.save(group);
        shoppingItemRepository.deleteById(shoppingItem.getId());
    }

    private void verifyIsPartOfGroup(User user, Group group) throws NotOwnerOrMemberOfGroupException {
        if (!(group.getOwner().equals(user) || group.getMembers().contains(user))) {
            throw new NotOwnerOrMemberOfGroupException("User with ID " + user.getId() + " is either a member nor the owner of the group");
        }
    }

    private void verifyShoppingListIsPartOfGroup(ShoppingList shoppingList, Group group) throws ShoppingListDoesNotBelongToGroupException {
        if (!shoppingList.getGroup().equals(group)) {
            throw new ShoppingListDoesNotBelongToGroupException("Shoppinglist with Id " + shoppingList.getId() + " does not belong to group with Id " + group.getId());
        }
    }

    private void verifyShoppingItemIsPartOfShoppingList(ShoppingList shoppingList, ShoppingItem shoppingItem) throws ShoppingItemDoesNotBelongToShoppingListException {
        if (!shoppingList.getId().equals(shoppingItem.getShoppingList().getId())) {
            throw new ShoppingItemDoesNotBelongToShoppingListException("Shoppingitem with Id " + shoppingItem.getId() + " does not belong to shoppinglist with Id " + shoppingList.getId());
        }
    }

}
