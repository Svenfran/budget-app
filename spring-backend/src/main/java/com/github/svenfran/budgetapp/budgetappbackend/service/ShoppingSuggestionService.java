package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingSuggestionsDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingItem;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.helper.ShoppingSuggestionRanker;
import com.github.svenfran.budgetapp.budgetappbackend.helper.Translator;
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingPurchaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

/**
 * Einkaufsvorschläge einer Liste (DIV-23): aus ihren Käufen, solange sie keine hat aus den Standardvorschlägen
 * (sprachabhängig über die Message-Bundles).
 */
@Service
public class ShoppingSuggestionService {

    static final String DEFAULT_SUGGESTIONS_KEY = "shopping.suggestions.defaults";

    private final DataLoaderService dataLoaderService;

    private final VerificationService verificationService;

    private final ShoppingPurchaseRepository shoppingPurchaseRepository;

    private final Translator translator;

    private final int limit;

    public ShoppingSuggestionService(DataLoaderService dataLoaderService, VerificationService verificationService,
                                     ShoppingPurchaseRepository shoppingPurchaseRepository, Translator translator,
                                     @Value("${app.shopping-suggestions.limit:15}") int limit) {
        this.dataLoaderService = dataLoaderService;
        this.verificationService = verificationService;
        this.shoppingPurchaseRepository = shoppingPurchaseRepository;
        this.translator = translator;
        this.limit = limit;
    }

    @Transactional(readOnly = true)
    public ShoppingSuggestionsDto getSuggestions(Long groupId, Long shoppingListId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException, ShoppingListNotFoundException, ShoppingListDoesNotBelongToGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verificationService.verifyIsPartOfGroup(user, group);
        var shoppingList = dataLoaderService.loadShoppingList(shoppingListId);
        verificationService.verifyShoppingListIsPartOfGroup(shoppingList, group);

        var existingNames = shoppingList.getShoppingItems().stream().map(ShoppingItem::getName).toList();
        var purchases = shoppingPurchaseRepository.findByShoppingListIdOrderByPurchasedAtDescIdDesc(shoppingList.getId());
        // Standardvorschläge nur ohne jeden Kauf (auch ohne namenlose): eine Liste mit Historie bekommt nur deren Vorschläge
        if (purchases.isEmpty()) {
            var defaults = Arrays.asList(translator.translate(DEFAULT_SUGGESTIONS_KEY).split(","));
            return new ShoppingSuggestionsDto(group.getId(), shoppingList.getId(), true,
                    ShoppingSuggestionRanker.filterDefaults(defaults, existingNames, limit));
        }
        return new ShoppingSuggestionsDto(group.getId(), shoppingList.getId(), false,
                ShoppingSuggestionRanker.rank(purchases, existingNames, limit));
    }
}
