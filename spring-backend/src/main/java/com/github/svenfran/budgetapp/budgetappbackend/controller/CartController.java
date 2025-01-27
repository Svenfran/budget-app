package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto;
import com.github.svenfran.budgetapp.budgetappbackend.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping("/carts/carts-by-groupid/{groupId}")
    public ResponseEntity<List<CartDto>> getCartsByGroupId(@PathVariable("groupId") Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        List<CartDto> cartDtoList = cartService.getCartsByGroupId(groupId);
        return new ResponseEntity<>(cartDtoList, HttpStatus.OK);
    }

    @GetMapping("/carts/{id}")
    public ResponseEntity<CartDto> getCartById(@PathVariable("id") Long id) throws CartNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException, GroupNotFoundException {
        CartDto cartDto = cartService.getCartById(id);
        return new ResponseEntity<>(cartDto, HttpStatus.OK);
    }

    @PostMapping("/carts/add")
    public ResponseEntity<CartDto> addCart(@RequestBody CartDto cartDto) throws Exception {
        CartDto newCart = cartService.addCart(cartDto);
        return new ResponseEntity<>(newCart, HttpStatus.CREATED);
    }

    @PutMapping("/carts/update")
    public ResponseEntity<CartDto> updateCart(@RequestBody CartDto cartDto) throws Exception {
        CartDto updateCart = cartService.updateCart(cartDto);
        return new ResponseEntity<>(updateCart, HttpStatus.CREATED);
    }

    @DeleteMapping("/carts/delete/{id}")
    public ResponseEntity<CartDto> deleteCart(@PathVariable("id") Long id) throws UserNotFoundException, GroupNotFoundException, CartNotFoundException, NotOwnerOfCartException, NotOwnerOrMemberOfGroupException {
        cartService.deleteCart(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("/carts/settlement-payment/add")
    public ResponseEntity<CartDto> addSettlementPayment(@RequestBody SettlementPaymentDto settlementPaymentDto) throws Exception {
        cartService.addSettlementPayment(settlementPaymentDto);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/carts/download/{groupId}")
    public void getExcelFile(@PathVariable("groupId") Long groupId, HttpServletResponse response) throws IOException, UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, IllegalAccessException {
        cartService.getExcelFile(response, groupId);
    }
}
