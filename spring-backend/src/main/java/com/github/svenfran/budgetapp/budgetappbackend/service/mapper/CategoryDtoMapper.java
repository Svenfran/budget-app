package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import org.springframework.stereotype.Service;

@Service
public class CategoryDtoMapper {

    public Category CategoryDtoToEntity(CategoryDto dto, Group group) {
        var category = new Category();
        category.setId(dto.getId());
        category.setName(dto.getName());
        category.setGroup(group);
        return category;
    }
}
