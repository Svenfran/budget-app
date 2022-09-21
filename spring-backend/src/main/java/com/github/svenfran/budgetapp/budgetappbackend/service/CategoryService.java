package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dao.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    public List<CategoryDto> getAllCategories() {
        List<Category> categoryList = categoryRepository.findAll();
        List<CategoryDto> categoryDtoList = new ArrayList<>();
        for (Category category : categoryList) {
            var categoryDto = new CategoryDto(category);
            categoryDtoList.add(categoryDto);
        }
        return categoryDtoList;
    }

    public CategoryDto getCategoryById(Long id) {
        return new CategoryDto(categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category with id " + id + " not found")));
    }

    public CategoryDto addCategory(CategoryDto categoryDto) {
        Category newCategory = new Category();
        newCategory.setName(categoryDto.getName());
        return new CategoryDto(categoryRepository.save(newCategory));
    }

    public CategoryDto updateCategory(CategoryDto categoryDto) {
        Category updateCategory = new Category();
        updateCategory.setId(categoryDto.getId());
        updateCategory.setName(categoryDto.getName());
        return new CategoryDto(categoryRepository.save(updateCategory));
    }

    public void deleteCategory(Long id) {
        categoryRepository.deleteById(getCategoryById(id).getId());
    }

}
