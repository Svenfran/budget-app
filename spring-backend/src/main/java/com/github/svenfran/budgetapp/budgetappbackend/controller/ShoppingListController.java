package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingListOrderDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingSuggestionsDto;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.service.ShoppingListService;
import com.github.svenfran.budgetapp.budgetappbackend.service.ShoppingSuggestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    private final ShoppingSuggestionService shoppingSuggestionService;

    public ShoppingListController(ShoppingListService shoppingListService,
                                  ShoppingSuggestionService shoppingSuggestionService) {
        this.shoppingListService = shoppingListService;
        this.shoppingSuggestionService = shoppingSuggestionService;
    }

    @GetMapping("/groups/shopping-lists-with-items/{groupId}")
    public ResponseEntity<List<ShoppingListDto>> getShoppingListsForGroup(@PathVariable("groupId") Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        List<ShoppingListDto> groupShoppingList = shoppingListService.getShoppingListsForGroup(groupId);
        return new ResponseEntity<>(groupShoppingList, HttpStatus.OK);
    }

    @GetMapping("/groups/shopping-list/suggestions/{groupId}/{shoppingListId}")
    public ResponseEntity<ShoppingSuggestionsDto> getShoppingSuggestions(@PathVariable("groupId") Long groupId, @PathVariable("shoppingListId") Long shoppingListId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListNotFoundException, ShoppingListDoesNotBelongToGroupException {
        ShoppingSuggestionsDto suggestions = shoppingSuggestionService.getSuggestions(groupId, shoppingListId);
        return new ResponseEntity<>(suggestions, HttpStatus.OK);
    }

    @PostMapping("/groups/shopping-list/add")
    public ResponseEntity<AddEditShoppingListDto> addShoppingList(@RequestBody AddEditShoppingListDto addEditShoppingListDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        AddEditShoppingListDto newShoppingList = shoppingListService.addShoppingList(addEditShoppingListDto);
        return new ResponseEntity<>(newShoppingList, HttpStatus.CREATED);
    }

    @PutMapping("/groups/shopping-list/update")
    public ResponseEntity<AddEditShoppingListDto> updateShoppingList(@RequestBody AddEditShoppingListDto addEditShoppingListDto) throws Exception {
        AddEditShoppingListDto updatedShoppingList = shoppingListService.updateShoppingList(addEditShoppingListDto);
        return new ResponseEntity<>(updatedShoppingList, HttpStatus.CREATED);
    }

    @PutMapping("/groups/shopping-list/reorder")
    public ResponseEntity<ShoppingListOrderDto> reorderShoppingLists(@RequestBody ShoppingListOrderDto shoppingListOrderDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        ShoppingListOrderDto order = shoppingListService.reorderShoppingLists(shoppingListOrderDto);
        return new ResponseEntity<>(order, HttpStatus.OK);
    }

    @PostMapping("/groups/shopping-list/delete")
    public ResponseEntity<AddEditShoppingListDto> deleteShoppingList(@RequestBody AddEditShoppingListDto addEditShoppingListDto) throws Exception {
        shoppingListService.deleteShoppingList(addEditShoppingListDto);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
