package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingListDto;
import com.github.svenfran.budgetapp.budgetappbackend.service.ShoppingListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ShoppingListController {

    @Autowired
    private ShoppingListService shoppingListService;

    @GetMapping("/groups/shopping-lists-with-items/{groupId}")
    public ResponseEntity<List<ShoppingListDto>> getShoppingListsForGroup(
            @PathVariable("groupId") Long groupId, @RequestParam(value = "requestTimeStamp", required = false) Long requestTimeStamp) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, InterruptedException {
        Optional<List<ShoppingListDto>> groupShoppingList = shoppingListService.getShoppingListsForGroup(groupId, requestTimeStamp);
        if (groupShoppingList.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
        }
        return new ResponseEntity<>(groupShoppingList.get(), HttpStatus.OK);
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

    @PostMapping("/groups/shopping-list/delete")
    public ResponseEntity<AddEditShoppingListDto> deleteShoppingList(@RequestBody AddEditShoppingListDto addEditShoppingListDto) throws Exception {
        shoppingListService.deleteShoppingList(addEditShoppingListDto);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
