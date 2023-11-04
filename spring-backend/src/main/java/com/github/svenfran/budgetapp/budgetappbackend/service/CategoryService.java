package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.CategoryDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryDtoMapper categoryDtoMapper;

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private VerificationService verificationService;


    public List<CategoryDto> getAllCategoriesByGroup(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verificationService.verifyIsPartOfGroup(user, group);
        var categoryList = dataLoaderService.loadCategoriesForGroup(groupId);
        return categoryList.stream().map(CategoryDto::new).toList();
    }

    public CategoryDto addCategory(@Validated CategoryDto categoryDto) throws GroupNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(categoryDto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        return new CategoryDto(categoryRepository.save(categoryDtoMapper.CategoryDtoToEntity(categoryDto, group)));
    }

    public CategoryDto updateCategory(@Validated CategoryDto categoryDto) throws GroupNotFoundException, CategoryBelongsNotToGroupException, CategoryNotFoundException, NotOwnerOrMemberOfGroupException, UserNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(categoryDto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var category = dataLoaderService.loadCategory(categoryDto.getId());
        verificationService.verifyCategoryIsPartOfGroup(category, group);
        return new CategoryDto(categoryRepository.save(categoryDtoMapper.CategoryDtoToEntity(categoryDto, group)));
    }

    public void deleteCategory(@Validated CategoryDto categoryDto) throws GroupNotFoundException, CategoryBelongsNotToGroupException, CategoryNotFoundException, CategoryIsUsedByCartException, NotOwnerOrMemberOfGroupException, UserNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(categoryDto.getGroupId());
        verificationService.verifyIsPartOfGroup(user, group);
        var category = dataLoaderService.loadCategory(categoryDto.getId());
        verificationService.verifyCategoryIsPartOfGroup(category, group);
        verificationService.verifyCategoryNotInUse(category);
        categoryRepository.deleteById(category.getId());
    }

}
