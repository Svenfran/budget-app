package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto;
import com.github.svenfran.budgetapp.budgetappbackend.service.ShoppingItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ShoppingItemController {

    @Autowired
    private ShoppingItemService shoppingItemService;


    @PostMapping("/groups/shopping-list/add-item")
    public ResponseEntity<AddEditShoppingItemDto> addShoppingItem(@RequestBody AddEditShoppingItemDto shoppingItemDto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListDoesNotBelongToGroupException {
        AddEditShoppingItemDto newItem = shoppingItemService.addShoppingItem(shoppingItemDto);
        return new ResponseEntity<>(newItem, HttpStatus.CREATED);
    }

    @PutMapping("/groups/shopping-list/update-item")
    public ResponseEntity<AddEditShoppingItemDto> updateShoppingItem(@RequestBody AddEditShoppingItemDto shoppingItemDto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingItemNotFoundException, ShoppingItemDoesNotBelongToShoppingListException, ShoppingListDoesNotBelongToGroupException {
        AddEditShoppingItemDto updatedItem = shoppingItemService.updateShoppingItem(shoppingItemDto);
        return new ResponseEntity<>(updatedItem, HttpStatus.CREATED);
    }

    @DeleteMapping("/groups/shopping-list/delete-item")
    public ResponseEntity<AddEditShoppingItemDto> deleteShoppingItem(@RequestBody AddEditShoppingItemDto shoppingItemDto) throws UserNotFoundException, GroupNotFoundException, ShoppingListNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingItemNotFoundException, ShoppingListDoesNotBelongToGroupException, ShoppingItemDoesNotBelongToShoppingListException {
        shoppingItemService.deleteShoppingItem(shoppingItemDto);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
