package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/groups/categories/{id}")
    public ResponseEntity<List<CategoryDto>> getAllCategoriesByGroup(@PathVariable("id") Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        List<CategoryDto> categoryDtoList = categoryService.getAllCategoriesByGroup(groupId);
        return new ResponseEntity<>(categoryDtoList, HttpStatus.OK);
    }

    @PostMapping("/groups/category/add")
    public ResponseEntity<CategoryDto> addCategory(@RequestBody CategoryDto categoryDto) throws GroupNotFoundException, CategoryBelongsNotToGroupException, CategoryNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException {
        CategoryDto newCategory = categoryService.addCategory(categoryDto);
        return new ResponseEntity<>(newCategory, HttpStatus.CREATED);
    }

    @PutMapping("/groups/category/update")
    public ResponseEntity<CategoryDto> updateCategory(@RequestBody CategoryDto categoryDto) throws GroupNotFoundException, CategoryBelongsNotToGroupException, CategoryNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException {
        CategoryDto updateCategory = categoryService.updateCategory(categoryDto);
        return new ResponseEntity<>(updateCategory, HttpStatus.OK);
    }

    @PostMapping("/groups/category/delete")
    public ResponseEntity<CategoryDto> deleteCategory(@RequestBody CategoryDto categoryDto) throws CategoryNotFoundException, CategoryBelongsNotToGroupException, GroupNotFoundException, CategoryIsUsedByCartException, UserNotFoundException, NotOwnerOrMemberOfGroupException {
        categoryService.deleteCategory(categoryDto);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
