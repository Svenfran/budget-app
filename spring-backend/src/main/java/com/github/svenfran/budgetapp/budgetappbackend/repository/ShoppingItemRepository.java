package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingItem;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShoppingItemRepository extends CrudRepository<ShoppingItem, Long> {

    @Query("select coalesce(max(i.sortOrder), -1) from ShoppingItem i where i.shoppingList.id = :shoppingListId")
    int findMaxSortOrderByShoppingListId(@Param("shoppingListId") Long shoppingListId);

    @Query("select i.id from ShoppingItem i where i.shoppingList.id = :shoppingListId order by i.sortOrder, i.id")
    List<Long> findIdsInOrder(@Param("shoppingListId") Long shoppingListId);

    @Modifying
    @Query("update ShoppingItem i set i.sortOrder = :sortOrder where i.id = :id and i.shoppingList.id = :shoppingListId")
    void updateSortOrder(@Param("id") Long id, @Param("shoppingListId") Long shoppingListId, @Param("sortOrder") int sortOrder);

    @Override
    <S extends ShoppingItem> S save(S entity);

    @Override
    Optional<ShoppingItem> findById(Long aLong);

    @Override
    void deleteById(Long aLong);

    @Override
    void deleteAllById(Iterable<? extends Long> longs);
}
