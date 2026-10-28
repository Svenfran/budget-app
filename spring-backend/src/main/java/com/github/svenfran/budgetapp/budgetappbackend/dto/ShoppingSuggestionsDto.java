package com.github.svenfran.budgetapp.budgetappbackend.dto;

import java.util.List;

public class ShoppingSuggestionsDto {

    private Long groupId;
    private Long shoppingListId;
    // true: die Liste hat noch keine Käufe, names sind die Standardvorschläge
    private boolean defaultSuggestions;
    private List<String> names;

    public ShoppingSuggestionsDto(Long groupId, Long shoppingListId, boolean defaultSuggestions, List<String> names) {
        this.groupId = groupId;
        this.shoppingListId = shoppingListId;
        this.defaultSuggestions = defaultSuggestions;
        this.names = names;
    }

    public ShoppingSuggestionsDto() {
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getShoppingListId() {
        return shoppingListId;
    }

    public void setShoppingListId(Long shoppingListId) {
        this.shoppingListId = shoppingListId;
    }

    public boolean isDefaultSuggestions() {
        return defaultSuggestions;
    }

    public void setDefaultSuggestions(boolean defaultSuggestions) {
        this.defaultSuggestions = defaultSuggestions;
    }

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }
}
