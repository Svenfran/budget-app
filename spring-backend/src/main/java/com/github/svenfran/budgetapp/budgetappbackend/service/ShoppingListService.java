package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;
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
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private DataLoaderService dataLoaderService;


    public List<ShoppingListDto> getShoppingListsForGroup(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getCurrentUser();
        var group = dataLoaderService.loadGroup(groupId);
        verifyIsPartOfGroup(user, group);
        return shoppingListRepository.findAllByGroup_IdOrderById(groupId).stream().map(ShoppingListDto::new).toList();
    }

    public AddEditShoppingListDto addShoppingList(AddEditShoppingListDto addEditShoppingListDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getCurrentUser();
        var group = dataLoaderService.loadGroup(addEditShoppingListDto.getGroupId());
        verifyIsPartOfGroup(user, group);
        return new AddEditShoppingListDto(shoppingListRepository.save(shoppingListDtoMapper.addEditShoppingListDtoToEntity(addEditShoppingListDto, group)));
    }

    public AddEditShoppingListDto updateShoppingList(AddEditShoppingListDto addEditShoppingListDto) throws Exception {
        var user = dataLoaderService.getCurrentUser();
        var group = dataLoaderService.loadGroup(addEditShoppingListDto.getGroupId());
        verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(addEditShoppingListDto.getId());
        verifyShoppingListIsPartOfGroup(shoppingList, group);
        return new AddEditShoppingListDto(shoppingListRepository.save(shoppingListDtoMapper.addEditShoppingListDtoToEntity(addEditShoppingListDto, group)));
    }

    @Transactional
    public void deleteShoppingList(AddEditShoppingListDto dto) throws Exception {
        var user = dataLoaderService.getCurrentUser();
        var group = dataLoaderService.loadGroup(dto.getGroupId());
        verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(dto.getId());
        verifyShoppingListIsPartOfGroup(shoppingList, group);
        shoppingItemRepository.deleteAll(shoppingList.getShoppingItems());
        shoppingListRepository.deleteById(shoppingList.getId());
    }

    private void verifyIsPartOfGroup(User user, Group group) throws NotOwnerOrMemberOfGroupException {
        if (!(group.getOwner().equals(user) || group.getMembers().contains(user))) {
            throw new NotOwnerOrMemberOfGroupException("User with ID " + user.getId() + " is either a member nor the owner of the group");
        }
    }

    private void verifyShoppingListIsPartOfGroup(ShoppingList shoppingList, Group group) throws ShoppingListDoesNotBelongToGroupException {
        if (!shoppingList.getGroup().equals(group)) {
            throw new ShoppingListDoesNotBelongToGroupException("Shoppinglist with Id " + shoppingList.getId() + " does not belong to group wiht Id " + group.getId());
        }
    }

}
