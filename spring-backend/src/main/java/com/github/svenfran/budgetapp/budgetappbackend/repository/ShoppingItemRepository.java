package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingItem;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShoppingItemRepository extends CrudRepository<ShoppingItem, Long> {

    @Query("select coalesce(max(i.sortOrder), -1) from ShoppingItem i where i.shoppingList.id = :shoppingListId")
    int findMaxSortOrderByShoppingListId(@Param("shoppingListId") Long shoppingListId);

    @Override
    <S extends ShoppingItem> S save(S entity);

    @Override
    Optional<ShoppingItem> findById(Long aLong);

    @Override
    void deleteById(Long aLong);

    @Override
    void deleteAllById(Iterable<? extends Long> longs);
}
