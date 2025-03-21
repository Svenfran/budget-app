package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.ShoppingItemDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public AddEditShoppingItemDto addShoppingItem(AddEditShoppingItemDto dto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListDoesNotBelongToGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(dto.getShoppingListId());
        verificationService.verifyShoppingListIsPartOfGroup(shoppingList, group);
        group.setLastUpdateShoppingList(new Date());
        groupRepository.save(group);
        var addedItem =  new AddEditShoppingItemDto(shoppingItemRepository.save(shoppingItemDtoMapper.addEditShoppingItemDtoToEntity(dto, shoppingList)));

        notificationService.sendShoppingItemNotification(
                group.getId(),
                addedItem,
                "add"
        );

        return addedItem;
    }

    @Transactional
    public AddEditShoppingItemDto updateShoppingItem(AddEditShoppingItemDto dto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingItemNotFoundException, ShoppingListDoesNotBelongToGroupException, ShoppingItemDoesNotBelongToShoppingListException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(dto.getShoppingListId());
        verificationService.verifyShoppingListIsPartOfGroup(shoppingList, group);
        var shoppingItem = dataLoaderService.loadShoppingItem(dto.getId());
        verificationService.verifyShoppingItemIsPartOfShoppingList(shoppingList, shoppingItem);
        group.setLastUpdateShoppingList(new Date());
        groupRepository.save(group);
        var updatedItem = new AddEditShoppingItemDto(shoppingItemRepository.save(shoppingItemDtoMapper.addEditShoppingItemDtoToEntity(dto, shoppingList)));

        notificationService.sendShoppingItemNotification(
                group.getId(),
                updatedItem,
                "update"
        );

        return updatedItem;
    }

    @Transactional
    public void deleteShoppingItem(AddEditShoppingItemDto dto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingItemNotFoundException, ShoppingListDoesNotBelongToGroupException, ShoppingItemDoesNotBelongToShoppingListException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(dto.getShoppingListId());
        verificationService.verifyShoppingListIsPartOfGroup(shoppingList, group);
        var shoppingItem = dataLoaderService.loadShoppingItem(dto.getId());
        verificationService.verifyShoppingItemIsPartOfShoppingList(shoppingList, shoppingItem);
        group.setLastUpdateShoppingList(new Date());
        groupRepository.save(group);
        shoppingItemRepository.deleteById(shoppingItem.getId());
        notificationService.sendShoppingItemNotification(
                group.getId(),
                dto,
                "delete"
        );
    }

    @Transactional
    public void deleteAllCompletedShoppingItems(List<AddEditShoppingItemDto> dtos) throws GroupNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListNotFoundException, ShoppingListDoesNotBelongToGroupException, ShoppingItemNotFoundException, ShoppingItemDoesNotBelongToShoppingListException {
        verificationService.verifyAllShoppingItemsBelongToSameShoppingListAndGroup(dtos);
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dtos.get(0).getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(dtos.get(0).getShoppingListId());
        verificationService.verifyShoppingListIsPartOfGroup(shoppingList, group);
        var shoppingItem = dataLoaderService.loadShoppingItem(dtos.get(0).getId());
        verificationService.verifyShoppingItemIsPartOfShoppingList(shoppingList, shoppingItem);
        var itemIds = new ArrayList<Long>(dtos.stream()
                .filter(AddEditShoppingItemDto::isCompleted)
                .map(AddEditShoppingItemDto::getId)
                .toList());
        group.setLastUpdateShoppingList(new Date());
        groupRepository.save(group);
        shoppingItemRepository.deleteAllById(itemIds);
        notificationService.sendShoppingItemDeleteAllNotification(
                group.getId(),
                dtos
        );
    }

}
