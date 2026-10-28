package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingPurchase;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface ShoppingPurchaseRepository extends CrudRepository<ShoppingPurchase, Long> {

    // Idempotent: ein bereits erfasster Eintrag (source_item_id) wird übersprungen statt einen Fehler auszulösen
    @Modifying
    @Query(value = """
            insert into shopping_purchase (group_id, shopping_list_id, item_name, purchased_at, source_item_id)
            values (:groupId, :shoppingListId, :itemName, :purchasedAt, :sourceItemId)
            on conflict (source_item_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("groupId") Long groupId, @Param("shoppingListId") Long shoppingListId,
                       @Param("itemName") String itemName, @Param("purchasedAt") Date purchasedAt,
                       @Param("sourceItemId") Long sourceItemId);

    List<ShoppingPurchase> findByShoppingListIdOrderByPurchasedAtDescIdDesc(Long shoppingListId);

    @Modifying
    @Query("delete from ShoppingPurchase p where p.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);
}
