package com.github.svenfran.budgetapp.budgetappbackend.dao;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface CartRepository extends CrudRepository<Cart, Long> {

    List<Cart> findAll();
}
