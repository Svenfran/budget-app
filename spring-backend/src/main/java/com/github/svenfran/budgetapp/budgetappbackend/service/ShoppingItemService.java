package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.ShoppingItemDtoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class ShoppingItemService {

    private final ShoppingItemDtoMapper shoppingItemDtoMapper;

    private final ShoppingItemRepository shoppingItemRepository;

    private final DataLoaderService dataLoaderService;

    private final GroupRepository groupRepository;

    private final VerificationService verificationService;

    private final NotificationService notificationService;

    private final Clock clock;

    public ShoppingItemService(ShoppingItemDtoMapper shoppingItemDtoMapper,
                               ShoppingItemRepository shoppingItemRepository, DataLoaderService dataLoaderService,
                               GroupRepository groupRepository, VerificationService verificationService,
                               NotificationService notificationService, Clock clock) {
        this.shoppingItemDtoMapper = shoppingItemDtoMapper;
        this.shoppingItemRepository = shoppingItemRepository;
        this.dataLoaderService = dataLoaderService;
        this.groupRepository = groupRepository;
        this.verificationService = verificationService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public AddEditShoppingItemDto addShoppingItem(AddEditShoppingItemDto dto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListDoesNotBelongToGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(dto.getShoppingListId());
        verificationService.verifyShoppingListIsPartOfGroup(shoppingList, group);
        // Gruppe sperren, bevor die Position gelesen wird: eine parallele Anlage oder Umsortierung wartet bis zum Commit
        groupRepository.touchShoppingLists(group.getId(), Date.from(clock.instant()));
        var newItem = shoppingItemDtoMapper.addEditShoppingItemDtoToEntity(dto, shoppingList);
        // Anlegen legt immer neu an: eine mitgesendete ID würde per merge einen bestehenden (auch fremden) Eintrag überschreiben
        newItem.setId(null);
        newItem.setSortOrder(shoppingItemRepository.findMaxSortOrderByShoppingListId(shoppingList.getId()) + 1);
        var addedItem = new AddEditShoppingItemDto(shoppingItemRepository.save(newItem));

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
        group.setLastUpdateShoppingList(Date.from(clock.instant()));
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
        group.setLastUpdateShoppingList(Date.from(clock.instant()));
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
        if (dtos.isEmpty()) { return; }
        verificationService.verifyAllShoppingItemsBelongToSameShoppingListAndGroup(dtos);
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dtos.get(0).getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(dtos.get(0).getShoppingListId());
        verificationService.verifyShoppingListIsPartOfGroup(shoppingList, group);
        // Jeden Eintrag prüfen (auch nicht erledigte), bevor etwas geschrieben wird: die Exceptions sind checked und
        // lösen keinen Rollback aus
        for (var itemDto : dtos) {
            var shoppingItem = dataLoaderService.loadShoppingItem(itemDto.getId());
            verificationService.verifyShoppingItemIsPartOfShoppingList(shoppingList, shoppingItem);
        }
        // Nur erledigte Einträge werden gelöscht und gemeldet: Empfänger entfernen alle übermittelten IDs
        var completedDtos = dtos.stream()
                .filter(AddEditShoppingItemDto::isCompleted)
                .toList();
        var itemIds = new ArrayList<>(completedDtos.stream()
                .map(AddEditShoppingItemDto::getId)
                .toList());
        group.setLastUpdateShoppingList(Date.from(clock.instant()));
        groupRepository.save(group);
        shoppingItemRepository.deleteAllById(itemIds);
        notificationService.sendShoppingItemDeleteAllNotification(
                group.getId(),
                completedDtos
        );
    }

}
