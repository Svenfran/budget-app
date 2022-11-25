package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface ShoppingListRepository extends CrudRepository<ShoppingList, Long> {

    List<ShoppingList> findAllByGroup_IdOrderById(Long groupId);

    @Override
    Optional<ShoppingList> findById(Long aLong);

    @Override
    <S extends ShoppingList> S save(S entity);

    @Override
    void deleteById(Long aLong);

}
