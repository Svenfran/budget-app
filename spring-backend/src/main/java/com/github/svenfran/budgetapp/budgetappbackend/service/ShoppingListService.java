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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class ShoppingListService {

    final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ShoppingListRepository shoppingListRepository;

    @Autowired
    private ShoppingListDtoMapper shoppingListDtoMapper;

    @Autowired
    private ShoppingItemRepository shoppingItemRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private VerificationService verificationService;

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
        group.setLastUpdateShoppingList(new Date());
        groupRepository.save(group);
        return new AddEditShoppingListDto(shoppingListRepository.save(shoppingListDtoMapper.addEditShoppingListDtoToEntity(addEditShoppingListDto, group)));
    }

    @Transactional
    public AddEditShoppingListDto updateShoppingList(AddEditShoppingListDto addEditShoppingListDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListDoesNotBelongToGroupException, ShoppingListNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(addEditShoppingListDto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(addEditShoppingListDto.getId());
        verificationService.verifyShoppingListIsPartOfGroup(shoppingList, group);
        group.setLastUpdateShoppingList(new Date());
        groupRepository.save(group);
        return new AddEditShoppingListDto(shoppingListRepository.save(shoppingListDtoMapper.addEditShoppingListDtoToEntity(addEditShoppingListDto, group)));
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
        group.setLastUpdateShoppingList(new Date());
        groupRepository.save(group);
    }

}
