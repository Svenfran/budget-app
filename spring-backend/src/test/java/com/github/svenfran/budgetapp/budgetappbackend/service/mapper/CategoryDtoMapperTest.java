package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategoryDtoMapperTest {

    private final CategoryDtoMapper categoryDtoMapper = new CategoryDtoMapper();

    @Test
    void categoryDtoToEntity_positive() {
        var categoryEntity = categoryDtoMapper.CategoryDtoToEntity(category());

        assertEquals(categoryEntity.getId(), 1L);
        assertEquals(categoryEntity.getName(), "TestCategory");
    }

    @Test
    void categoryDtoToEntity_negative() {
        var categoryEntity = categoryDtoMapper.CategoryDtoToEntity(category());

        assertNotEquals(categoryEntity.getId(), 5L);
        assertNotEquals(categoryEntity.getName(), "NewCategory");
    }

    private CategoryDto category() {
        var category = new CategoryDto();
        category.setId(1L);
        category.setName("TestCategory");
        return category;
    }
}
