package com.github.svenfran.budgetapp.budgetappbackend.service.mapper;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import org.springframework.stereotype.Service;

@Service
public class CategoryDtoMapper {

    public Category CategoryDtoToEntity(CategoryDto dto) {
        var category = new Category();
        category.setId(dto.getId());
        category.setName(dto.getName());
        return category;
    }
}
