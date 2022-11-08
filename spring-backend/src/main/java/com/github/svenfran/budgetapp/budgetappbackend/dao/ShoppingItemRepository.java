package com.github.svenfran.budgetapp.budgetappbackend.dao;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingItem;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface ShoppingItemRepository extends CrudRepository<ShoppingItem, Long> {

    @Override
    <S extends ShoppingItem> S save(S entity);

    @Override
    Optional<ShoppingItem> findById(Long aLong);

    @Override
    void deleteById(Long aLong);
}
