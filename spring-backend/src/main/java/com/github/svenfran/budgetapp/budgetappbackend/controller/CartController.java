package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
