package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CategoryDtoMapperTest {

    private final CategoryDtoMapper categoryDtoMapper = new CategoryDtoMapper();

    @Test
    void categoryDtoToEntity_positive() {
        var categoryEntity = categoryDtoMapper.CategoryDtoToEntity(category(), group());

        assertEquals(1L, categoryEntity.getId());
        assertEquals(10L, categoryEntity.getGroup().getId());
        assertEquals("NewGroup", categoryEntity.getGroup().getName());
        assertEquals("TestCategory", categoryEntity.getName());
    }

    @Test
    void categoryDtoToEntity_negative() {
        var categoryEntity = categoryDtoMapper.CategoryDtoToEntity(category(), group());

        assertNotEquals(5L, categoryEntity.getId());
        assertNotEquals(15L, categoryEntity.getGroup().getId());
        assertNotEquals("TestGroup", categoryEntity.getGroup().getName());
        assertNotEquals("NewCategory", categoryEntity.getName());
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
