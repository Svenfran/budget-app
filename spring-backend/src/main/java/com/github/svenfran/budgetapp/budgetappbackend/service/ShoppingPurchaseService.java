package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingItem;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingPurchaseRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collection;
import java.util.Date;

/**
 * Einkaufshistorie (DIV-23): ein Kauf ist ein serverseitig erledigter Eintrag, der aus seiner Liste entfernt wird.
 * Aufrufer sind die Löschpfade, jeweils vor dem Löschen und in ihrer Transaktion.
 */
@Service
public class ShoppingPurchaseService {

    private final ShoppingPurchaseRepository shoppingPurchaseRepository;

    private final Clock clock;

    public ShoppingPurchaseService(ShoppingPurchaseRepository shoppingPurchaseRepository, Clock clock) {
        this.shoppingPurchaseRepository = shoppingPurchaseRepository;
        this.clock = clock;
    }

    /** Erfasst die erledigten unter den Einträgen als Kauf; ein bereits erfasster Eintrag wird übersprungen. */
    public void recordPurchases(ShoppingList shoppingList, Collection<ShoppingItem> shoppingItems) {
        var now = Date.from(clock.instant());
        shoppingItems.stream()
                .filter(ShoppingItem::isCompleted)
                .forEach(item -> shoppingPurchaseRepository.insertIfAbsent(
                        shoppingList.getGroup().getId(), shoppingList.getId(), item.getName(), now, item.getId()));
    }
}
