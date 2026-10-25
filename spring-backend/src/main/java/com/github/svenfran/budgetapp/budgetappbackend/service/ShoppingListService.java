package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingListRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.ShoppingListDtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class ShoppingListService {

    final Logger logger = LoggerFactory.getLogger(this.getClass());

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

    public Optional<List<ShoppingListDto>> getShoppingListsForGroup(Long groupId, Long requestTimeStamp) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verificationService.verifyIsPartOfGroup(user, group);

        int pauseTime = 3000;
        int timeout = 9000;
        int currentWaitTime = 0;

        do {
            var shoppingList = compareForChanges(groupId, requestTimeStamp);
            if (shoppingList.isPresent()) {
                return shoppingList;
            }
            currentWaitTime += pauseTime;
            untilTimeIsUp(pauseTime);

        } while (currentWaitTime < timeout);
        return compareForChanges(groupId, requestTimeStamp);
    }

    private Optional<List<ShoppingListDto>> compareForChanges(Long groupId, Long requestTimeStamp) throws GroupNotFoundException {
        var group = dataLoaderService.loadGroup(groupId);
        var lastUpdate = group.getLastUpdateShoppingList();

        if (lastUpdate == null) lastUpdate = group.getDateCreated();

        if (lastUpdate != null && requestTimeStamp != null) {
            logger.info("request time: {}", requestTimeStamp);
            logger.info("last update : {}", lastUpdate);
        }

        if (requestTimeStamp != null && lastUpdate != null && requestTimeStamp > lastUpdate.getTime()) {
            return Optional.empty();
        }
        return Optional.of(shoppingListRepository.findAllByGroup_IdOrderById(groupId).stream().map(ShoppingListDto::new).toList());
    }

    private void untilTimeIsUp(long milliseconds){
        try {
            logger.info("waiting for '{}' seconds...", milliseconds/1000);
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            logger.warn("waiting for time interrupted!", e);
            Thread.currentThread().interrupt();
        }
    }

    @Transactional
    public AddEditShoppingListDto addShoppingList(AddEditShoppingListDto addEditShoppingListDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(addEditShoppingListDto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        group.setLastUpdateShoppingList(Date.from(clock.instant()));
        groupRepository.save(group);

        var newList = shoppingListDtoMapper.addEditShoppingListDtoToEntity(addEditShoppingListDto, group);
        // Anlegen legt immer neu an: eine mitgesendete ID würde per merge eine bestehende (auch fremde) Liste überschreiben
        newList.setId(null);
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

}
