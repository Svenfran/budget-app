package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategoryDtoMapperTest {

    private final CategoryDtoMapper categoryDtoMapper = new CategoryDtoMapper();

    @Test
    void categoryDtoToEntity_positive() {
        var categoryEntity = categoryDtoMapper.CategoryDtoToEntity(category(), group());

        assertEquals(categoryEntity.getId(), 1L);
        assertEquals(categoryEntity.getGroup().getId(), 10L);
        assertEquals(categoryEntity.getGroup().getName(), "NewGroup");
        assertEquals(categoryEntity.getName(), "TestCategory");
    }

    @Test
    void categoryDtoToEntity_negative() {
        var categoryEntity = categoryDtoMapper.CategoryDtoToEntity(category(), group());

        assertNotEquals(categoryEntity.getId(), 5L);
        assertNotEquals(categoryEntity.getGroup().getId(), 15L);
        assertNotEquals(categoryEntity.getGroup().getName(), "TestGroup");
        assertNotEquals(categoryEntity.getName(), "NewCategory");
    }

    private CategoryDto category() {
        var category = new CategoryDto();
        category.setId(1L);
        category.setName("TestCategory");
        return category;
    }

    private Group group() {
        var group = new Group();
        group.setId(10L);
        group.setName("NewGroup");
        return group;
    }
}
