package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingListOrderDto;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.helper.SortOrderMerger;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingListRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.ShoppingListDtoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Date;
import java.util.List;

@Service
public class ShoppingListService {

    private final ShoppingListRepository shoppingListRepository;

    private final ShoppingListDtoMapper shoppingListDtoMapper;

    private final ShoppingItemRepository shoppingItemRepository;

    private final GroupRepository groupRepository;

    private final DataLoaderService dataLoaderService;

    private final VerificationService verificationService;

    private final NotificationService notificationService;

    private final Clock clock;

    public ShoppingListService(ShoppingListRepository shoppingListRepository,
                               ShoppingListDtoMapper shoppingListDtoMapper,
                               ShoppingItemRepository shoppingItemRepository, GroupRepository groupRepository,
                               DataLoaderService dataLoaderService, VerificationService verificationService,
                               NotificationService notificationService, Clock clock) {
        this.shoppingListRepository = shoppingListRepository;
        this.shoppingListDtoMapper = shoppingListDtoMapper;
        this.shoppingItemRepository = shoppingItemRepository;
        this.groupRepository = groupRepository;
        this.dataLoaderService = dataLoaderService;
        this.verificationService = verificationService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    public List<ShoppingListDto> getShoppingListsForGroup(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verificationService.verifyIsPartOfGroup(user, group);

        // Revision vor den Listen lesen: sie ist dann nie neuer als die gelieferte Reihenfolge
        var listOrderRevision = groupRepository.findListOrderRevision(groupId);
        return shoppingListRepository.findAllByGroup_IdOrderBySortOrderAscIdAsc(groupId).stream()
                .map(shoppingList -> new ShoppingListDto(shoppingList, listOrderRevision))
                .toList();
    }

    @Transactional
    public AddEditShoppingListDto addShoppingList(AddEditShoppingListDto addEditShoppingListDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(addEditShoppingListDto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        // Gruppe sperren, bevor die Position gelesen wird: eine parallele Anlage oder Umsortierung wartet bis zum Commit
        groupRepository.touchShoppingLists(group.getId(), Date.from(clock.instant()));

        var newList = shoppingListDtoMapper.addEditShoppingListDtoToEntity(addEditShoppingListDto, group);
        // Anlegen legt immer neu an: eine mitgesendete ID würde per merge eine bestehende (auch fremde) Liste überschreiben
        newList.setId(null);
        newList.setSortOrder(shoppingListRepository.findMaxSortOrderByGroupId(group.getId()) + 1);
        var addedList = new AddEditShoppingListDto(shoppingListRepository.save(newList));

        notificationService.sendShoppingListNotification(
                group.getId(),
                addedList,
                "add"
        );

        return addedList;
    }

    @Transactional
    public AddEditShoppingListDto updateShoppingList(AddEditShoppingListDto addEditShoppingListDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListDoesNotBelongToGroupException, ShoppingListNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(addEditShoppingListDto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(addEditShoppingListDto.getId());
        verificationService.verifyShoppingListIsPartOfGroup(shoppingList, group);
        group.setLastUpdateShoppingList(Date.from(clock.instant()));
        groupRepository.save(group);

        var updatedList = new AddEditShoppingListDto(shoppingListRepository.save(shoppingListDtoMapper.addEditShoppingListDtoToEntity(addEditShoppingListDto, group)));

        notificationService.sendShoppingListNotification(
                group.getId(),
                updatedList,
                "update"
        );

        return updatedList;
    }

    @Transactional
    public void deleteShoppingList(AddEditShoppingListDto dto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListDoesNotBelongToGroupException, ShoppingListNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(dto.getId());
        verificationService.verifyShoppingListIsPartOfGroup(shoppingList, group);
        shoppingItemRepository.deleteAll(shoppingList.getShoppingItems());
        shoppingListRepository.deleteById(shoppingList.getId());
        group.setLastUpdateShoppingList(Date.from(clock.instant()));
        groupRepository.save(group);
        notificationService.sendShoppingListNotification(
                group.getId(),
                dto,
                "delete"
        );
    }

    /**
     * Speichert die Reihenfolge der Listen einer Gruppe (tolerant, siehe SortOrderMerger) und erhöht ihre Revision.
     * Sperrreihenfolge wie bei allen Änderungen der Einkaufslisten: zuerst die Gruppenzeile (erstes schreibendes
     * Statement), danach die Listen. Gleichzeitige Reorders und Anlagen einer Gruppe laufen dadurch nacheinander;
     * Revision und IDs werden erst nach der Sperre gelesen und sind damit der zuletzt committete Stand.
     */
    @Transactional
    public ShoppingListOrderDto reorderShoppingLists(ShoppingListOrderDto dto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        groupRepository.lockForListReorder(group.getId(), Date.from(clock.instant()));

        var revision = groupRepository.findListOrderRevision(group.getId());
        var ids = SortOrderMerger.merge(dto.getShoppingListIds(), shoppingListRepository.findIdsInOrder(group.getId()));
        for (int position = 0; position < ids.size(); position++) {
            shoppingListRepository.updateSortOrder(ids.get(position), group.getId(), position);
        }
        var order = new ShoppingListOrderDto(group.getId(), revision, ids);

        notificationService.sendShoppingListOrderNotification(group.getId(), order);

        return order;
    }

}
