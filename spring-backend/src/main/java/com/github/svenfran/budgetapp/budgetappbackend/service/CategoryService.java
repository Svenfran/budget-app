package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.CategoryNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.dao.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.CategoryDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryDtoMapper categoryDtoMapper;

    public List<CategoryDto> getAllCategories() {
        List<Category> categoryList = categoryRepository.findAll();
        return categoryList.stream().map(CategoryDto::new).toList();
    }

    public CategoryDto getCategoryById(Long id) throws CategoryNotFoundException {
        return new CategoryDto(categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category with id " + id + " not found")));
    }

    public CategoryDto addCategory(CategoryDto categoryDto) {
        return new CategoryDto(categoryRepository.save(categoryDtoMapper.CategoryDtoToEntity(categoryDto)));
    }

    public CategoryDto updateCategory(CategoryDto categoryDto) {
        return new CategoryDto(categoryRepository.save(categoryDtoMapper.CategoryDtoToEntity(categoryDto)));
    }

    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

}
