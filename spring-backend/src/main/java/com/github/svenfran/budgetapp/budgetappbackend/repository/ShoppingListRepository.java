package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShoppingListRepository extends CrudRepository<ShoppingList, Long> {

    List<ShoppingList> findAllByGroup_IdOrderBySortOrderAscIdAsc(Long groupId);

    @Query("select coalesce(max(s.sortOrder), -1) from ShoppingList s where s.group.id = :groupId")
    int findMaxSortOrderByGroupId(@Param("groupId") Long groupId);

    @Query("select s.id from ShoppingList s where s.group.id = :groupId order by s.sortOrder, s.id")
    List<Long> findIdsInOrder(@Param("groupId") Long groupId);

    @Modifying
    @Query("update ShoppingList s set s.sortOrder = :sortOrder where s.id = :id and s.group.id = :groupId")
    void updateSortOrder(@Param("id") Long id, @Param("groupId") Long groupId, @Param("sortOrder") int sortOrder);

    @Modifying
    @Query("update ShoppingList s set s.itemOrderRevision = s.itemOrderRevision + 1 where s.id = :id")
    void incrementItemOrderRevision(@Param("id") Long id);

    @Query("select s.itemOrderRevision from ShoppingList s where s.id = :id")
    long findItemOrderRevision(@Param("id") Long id);

    @Override
    Optional<ShoppingList> findById(Long aLong);

    @Override
    <S extends ShoppingList> S save(S entity);

    @Override
    void deleteById(Long aLong);

}
