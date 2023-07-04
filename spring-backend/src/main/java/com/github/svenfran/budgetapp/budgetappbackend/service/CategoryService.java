package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
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


    public List<CategoryDto> getAllCategoriesByGroup(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verifyIsPartOfGroup(user, group);
        var categoryList = dataLoaderService.loadCategoriesForGroup(groupId);
        return categoryList.stream().map(CategoryDto::new).toList();
    }

    public CategoryDto addCategory(@Validated CategoryDto categoryDto) throws GroupNotFoundException, UserNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(categoryDto.getGroupId());
        verifyIsPartOfGroup(user, group);
        return new CategoryDto(categoryRepository.save(categoryDtoMapper.CategoryDtoToEntity(categoryDto, group)));
    }

    public CategoryDto updateCategory(@Validated CategoryDto categoryDto) throws GroupNotFoundException, CategoryBelongsNotToGroupException, CategoryNotFoundException, NotOwnerOrMemberOfGroupException, UserNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(categoryDto.getGroupId());
        verifyIsPartOfGroup(user, group);
        var category = dataLoaderService.loadCategory(categoryDto.getId());
        verifyCategoryIsPartOfGroup(category, group);
        return new CategoryDto(categoryRepository.save(categoryDtoMapper.CategoryDtoToEntity(categoryDto, group)));
    }

    public void deleteCategory(@Validated CategoryDto categoryDto) throws GroupNotFoundException, CategoryBelongsNotToGroupException, CategoryNotFoundException, CategoryIsUsedByCartException, NotOwnerOrMemberOfGroupException, UserNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(categoryDto.getGroupId());
        verifyIsPartOfGroup(user, group);
        var category = dataLoaderService.loadCategory(categoryDto.getId());
        verifyCategoryIsPartOfGroup(category, group);
        verifyCategoryNotInUse(category);
        categoryRepository.deleteById(category.getId());
    }

    private void verifyIsPartOfGroup(User user, Group group) throws NotOwnerOrMemberOfGroupException {
        if (!(group.getOwner().equals(user) || group.getMembers().contains(user))) {
            throw new NotOwnerOrMemberOfGroupException("User with ID " + user.getId() + " is either a member nor the owner of the group");
        }
    }

    private void verifyCategoryIsPartOfGroup(Category category, Group group) throws CategoryBelongsNotToGroupException {
        if (!group.getId().equals(category.getGroup().getId())) {
            throw new CategoryBelongsNotToGroupException("Category with Id " + category.getId() + " does not belong to group with Id " + group.getId());
        }
    }

    private void verifyCategoryNotInUse(Category category) throws CategoryIsUsedByCartException {
        if (!category.getCarts().isEmpty()) {
            throw new CategoryIsUsedByCartException("Category with Id " + category.getId() + " is used by carts");
        }
    }

}
