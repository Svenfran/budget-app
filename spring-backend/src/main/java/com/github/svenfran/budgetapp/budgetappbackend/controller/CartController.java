package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.AddCartCategoryNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.CartNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.UpdateCartCategoryNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping("/carts")
    public ResponseEntity<List<CartDto>> getAllCarts() {
        List<CartDto> cartDtoList = cartService.getAllCarts();
        return new ResponseEntity<>(cartDtoList, HttpStatus.OK);
    }

    @GetMapping("/carts/{id}")
    public ResponseEntity<CartDto> getCartById(@PathVariable("id") Long id) throws CartNotFoundException {
        CartDto cartDto = cartService.getCartById(id);
        return new ResponseEntity<>(cartDto, HttpStatus.OK);
    }

    @PostMapping("/carts/add")
    public ResponseEntity<CartDto> addCart(@RequestBody CartDto cartDto) throws AddCartCategoryNotFoundException {
        CartDto newCart = cartService.addCart(cartDto);
        return new ResponseEntity<>(newCart, HttpStatus.CREATED);
    }

    @PutMapping("/carts/update")
    public ResponseEntity<CartDto> updateCart(@RequestBody CartDto cartDto) throws UpdateCartCategoryNotFoundException {
        CartDto updateCart = cartService.updateCart(cartDto);
        return new ResponseEntity<>(updateCart, HttpStatus.CREATED);
    }

    @DeleteMapping("/carts/delete/{id}")
    public ResponseEntity<CartDto> deleteCart(@PathVariable("id") Long id) {
        cartService.deleteCart(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
