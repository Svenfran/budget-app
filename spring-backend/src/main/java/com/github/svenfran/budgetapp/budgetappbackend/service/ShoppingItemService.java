package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.MoveShoppingItemDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingItemMovedDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingItemOrderDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingItem;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.helper.SortOrderMerger;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingListRepository;
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

    private final ShoppingListRepository shoppingListRepository;

    private final DataLoaderService dataLoaderService;

    private final GroupRepository groupRepository;

    private final VerificationService verificationService;

    private final NotificationService notificationService;

    private final ShoppingPurchaseService shoppingPurchaseService;

    private final Clock clock;

    public ShoppingItemService(ShoppingItemDtoMapper shoppingItemDtoMapper,
                               ShoppingItemRepository shoppingItemRepository,
                               ShoppingListRepository shoppingListRepository, DataLoaderService dataLoaderService,
                               GroupRepository groupRepository, VerificationService verificationService,
                               NotificationService notificationService,
                               ShoppingPurchaseService shoppingPurchaseService, Clock clock) {
        this.shoppingItemDtoMapper = shoppingItemDtoMapper;
        this.shoppingItemRepository = shoppingItemRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.dataLoaderService = dataLoaderService;
        this.groupRepository = groupRepository;
        this.verificationService = verificationService;
        this.notificationService = notificationService;
        this.shoppingPurchaseService = shoppingPurchaseService;
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
        // Gruppe sperren, bevor der Eintrag gelesen wird: ein parallel committeter Move oder ein paralleles Löschen ist
        // dann sichtbar, und der Kauf wird mit dem zuletzt committeten Erledigt-Status erfasst
        groupRepository.touchShoppingLists(group.getId(), Date.from(clock.instant()));
        var shoppingItem = dataLoaderService.loadShoppingItem(dto.getId());
        verificationService.verifyShoppingItemIsPartOfShoppingList(shoppingList, shoppingItem);
        shoppingPurchaseService.recordPurchases(shoppingList, List.of(shoppingItem));
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
        // Gruppe sperren, bevor die Einträge gelesen werden (wie beim Einzel-Löschen)
        groupRepository.touchShoppingLists(group.getId(), Date.from(clock.instant()));
        // Jeden Eintrag prüfen (auch nicht erledigte), bevor die Einträge geschrieben werden: die Exceptions sind
        // checked und lösen keinen Rollback aus (bestehen bleibt nur der Zeitstempel der Sperre)
        var shoppingItems = new ArrayList<ShoppingItem>();
        for (var itemDto : dtos) {
            var shoppingItem = dataLoaderService.loadShoppingItem(itemDto.getId());
            verificationService.verifyShoppingItemIsPartOfShoppingList(shoppingList, shoppingItem);
            shoppingItems.add(shoppingItem);
        }
        // Nur als erledigt gesendete Einträge werden gelöscht und gemeldet: Empfänger entfernen alle übermittelten IDs
        var completedDtos = dtos.stream()
                .filter(AddEditShoppingItemDto::isCompleted)
                .toList();
        var itemIds = new ArrayList<>(completedDtos.stream()
                .map(AddEditShoppingItemDto::getId)
                .toList());
        // Als Kauf zählt davon nur, was auch serverseitig erledigt ist
        shoppingPurchaseService.recordPurchases(shoppingList, shoppingItems.stream()
                .filter(item -> itemIds.contains(item.getId()))
                .toList());
        shoppingItemRepository.deleteAllById(itemIds);
        notificationService.sendShoppingItemDeleteAllNotification(
                group.getId(),
                completedDtos
        );
    }

    /**
     * Speichert die Reihenfolge der Einträge einer Liste (tolerant, siehe SortOrderMerger; Einträge anderer Listen
     * bleiben unverändert) und erhöht die Revision der Liste. Sperrreihenfolge Gruppe → Liste → Einträge wie beim
     * Reorder der Listen, damit sich beide Reorders nicht gegenseitig blockieren.
     */
    @Transactional
    public ShoppingItemOrderDto reorderShoppingItems(ShoppingItemOrderDto dto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListNotFoundException, ShoppingListDoesNotBelongToGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(dto.getShoppingListId());
        verificationService.verifyShoppingListIsPartOfGroup(shoppingList, group);
        groupRepository.touchShoppingLists(group.getId(), Date.from(clock.instant()));
        shoppingListRepository.incrementItemOrderRevision(shoppingList.getId());

        var revision = shoppingListRepository.findItemOrderRevision(shoppingList.getId());
        var ids = SortOrderMerger.merge(dto.getShoppingItemIds(), shoppingItemRepository.findIdsInOrder(shoppingList.getId()));
        for (int position = 0; position < ids.size(); position++) {
            shoppingItemRepository.updateSortOrder(ids.get(position), shoppingList.getId(), position);
        }
        var order = new ShoppingItemOrderDto(group.getId(), shoppingList.getId(), revision, ids);

        notificationService.sendShoppingItemOrderNotification(group.getId(), order);

        return order;
    }

    /**
     * Verschiebt einen Eintrag in eine andere Liste der Gruppe: ändert die Zuordnung, normalisiert die Quellliste,
     * speichert die gewünschte Reihenfolge der Zielliste (tolerant, siehe SortOrderMerger) und erhöht beide
     * Revisionen. Sperrreihenfolge wie beim Reorder: Gruppe → Listen (aufsteigend nach id) → Einträge. Eintrag und
     * Zielliste werden erst nach der Gruppensperre geprüft, sodass ein parallel committeter Move oder eine gelöschte
     * Zielliste zu 404 führt. Die Eintragsdaten bleiben unverändert (Bulk-Updates, kein @UpdateTimestamp).
     */
    @Transactional
    public ShoppingItemMovedDto moveShoppingItem(MoveShoppingItemDto dto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListNotFoundException, ShoppingListDoesNotBelongToGroupException, ShoppingItemMoveWithinSameListException, ShoppingItemNotFoundException, ShoppingItemDoesNotBelongToShoppingListException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var source = dataLoaderService.loadShoppingList(dto.getSourceShoppingListId());
        verificationService.verifyShoppingListIsPartOfGroup(source, group);
        var target = dataLoaderService.loadShoppingList(dto.getTargetShoppingListId());
        verificationService.verifyShoppingListIsPartOfGroup(target, group);
        if (source.getId().equals(target.getId())) {
            throw new ShoppingItemMoveWithinSameListException("Shoppingitem with Id " + dto.getShoppingItemId() + " cannot be moved within shopping list with Id " + source.getId());
        }
        groupRepository.touchShoppingLists(group.getId(), Date.from(clock.instant()));
        var shoppingItem = dataLoaderService.loadShoppingItem(dto.getShoppingItemId());
        verificationService.verifyShoppingItemIsPartOfShoppingList(source, shoppingItem);
        if (!shoppingListRepository.existsById(target.getId())) {
            throw new ShoppingListNotFoundException(String.format("Shoppinglist with Id %s not found", target.getId()));
        }
        shoppingListRepository.incrementItemOrderRevision(Math.min(source.getId(), target.getId()));
        shoppingListRepository.incrementItemOrderRevision(Math.max(source.getId(), target.getId()));
        if (shoppingItemRepository.moveToList(shoppingItem.getId(), source, target) != 1) {
            throw new IllegalStateException("Shoppingitem with Id " + shoppingItem.getId() + " could not be moved to shopping list with Id " + target.getId());
        }

        var sourceIds = shoppingItemRepository.findIdsInOrder(source.getId());
        for (int position = 0; position < sourceIds.size(); position++) {
            shoppingItemRepository.updateSortOrder(sourceIds.get(position), source.getId(), position);
        }
        var targetIds = SortOrderMerger.merge(dto.getTargetShoppingItemIds(), shoppingItemRepository.findIdsInOrder(target.getId()));
        for (int position = 0; position < targetIds.size(); position++) {
            shoppingItemRepository.updateSortOrder(targetIds.get(position), target.getId(), position);
        }
        var moved = new ShoppingItemMovedDto(group.getId(), shoppingItem.getId(),
                new ShoppingItemOrderDto(group.getId(), source.getId(), shoppingListRepository.findItemOrderRevision(source.getId()), sourceIds),
                new ShoppingItemOrderDto(group.getId(), target.getId(), shoppingListRepository.findItemOrderRevision(target.getId()), targetIds));

        notificationService.sendShoppingItemMoveNotification(group.getId(), moved);

        return moved;
    }

}
