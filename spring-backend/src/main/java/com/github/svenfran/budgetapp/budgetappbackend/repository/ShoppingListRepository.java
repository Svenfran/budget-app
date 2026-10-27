package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShoppingListRepository extends CrudRepository<ShoppingList, Long> {

    List<ShoppingList> findAllByGroup_IdOrderBySortOrderAscIdAsc(Long groupId);

    @Query("select coalesce(max(s.sortOrder), -1) from ShoppingList s where s.group.id = :groupId")
    int findMaxSortOrderByGroupId(@Param("groupId") Long groupId);

    @Override
    Optional<ShoppingList> findById(Long aLong);

    @Override
    <S extends ShoppingList> S save(S entity);

    @Override
    void deleteById(Long aLong);

}
