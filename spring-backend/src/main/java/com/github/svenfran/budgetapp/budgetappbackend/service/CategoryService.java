package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
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

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    public List<CategoryDto> getAllCategoriesByGroup(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(groupId).
                orElseThrow(() -> new GroupNotFoundException("Get Categories: Group not found"));

        if (group.getOwner().equals(user) || group.getMembers().contains(user)) {
            List<Category> categoryList = categoryRepository.findAllByGroup_IdOrderByName(groupId);
            return categoryList.stream().map(CategoryDto::new).toList();
        } else throw new NotOwnerOrMemberOfGroupException("Get Categories: You are either the owner nor a member of the group");
    }

    public CategoryDto addCategory(CategoryDto categoryDto) throws GroupNotFoundException, CategoryBelongsNotToGroupException, UserNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(categoryDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Add Category: Group not found"));

        if (!group.getOwner().equals(user) || group.getMembers().contains(user)) {
            throw new NotOwnerOrMemberOfGroupException("Add Category: You are either the owner nor a member of the group");
        } else if (group.getId().equals(categoryDto.getGroupId())) {
            return new CategoryDto(categoryRepository.save(categoryDtoMapper.CategoryDtoToEntity(categoryDto, group)));
        } else throw new CategoryBelongsNotToGroupException("Add Category: Category does not belong to group");
    }

    public CategoryDto updateCategory(CategoryDto categoryDto) throws GroupNotFoundException, CategoryBelongsNotToGroupException, CategoryNotFoundException, NotOwnerOrMemberOfGroupException, UserNotFoundException {
        var user = getCurrentUser();
        var group = groupRepository.findById(categoryDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Update Category: Group not found"));
        var category = categoryRepository.findById(categoryDto.getId()).
                orElseThrow(() -> new CategoryNotFoundException("Update Category: Category not found"));

        if (!group.getOwner().equals(user) || group.getMembers().contains(user)) {
            throw new NotOwnerOrMemberOfGroupException("Update Category: You are either the owner nor a member of the group");
        } else if (group.getId().equals(category.getGroup().getId())) {
            return new CategoryDto(categoryRepository.save(categoryDtoMapper.CategoryDtoToEntity(categoryDto, group)));
        } else throw new CategoryBelongsNotToGroupException("Update Category: Category does not belong to group");
    }

    public void deleteCategory(CategoryDto categoryDto) throws GroupNotFoundException, CategoryBelongsNotToGroupException, CategoryNotFoundException, CategoryIsUsedByCartException, NotOwnerOrMemberOfGroupException, UserNotFoundException {
        var user = getCurrentUser();
        var group = groupRepository.findById(categoryDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Delete Category: Group not found"));
        var category = categoryRepository.findById(categoryDto.getId()).
                orElseThrow(() -> new CategoryNotFoundException("Delete Category: Category not found"));

        if (!group.getOwner().equals(user) || group.getMembers().contains(user)) {
            throw new NotOwnerOrMemberOfGroupException("Delete Category: You are either the owner nor a member of the group");
        } else if (!group.getId().equals(category.getGroup().getId())) {
            throw new CategoryBelongsNotToGroupException("Delete Category: Category does not belong to group");
        } else if (category.getCarts().isEmpty()) {
            categoryRepository.deleteById(category.getId());
        } else throw new CategoryIsUsedByCartException("Delete Category: Category is used by carts");
    }

    // TODO: Derzeit angemeldete Nutzer -> Spring Security
    private User getCurrentUser() throws UserNotFoundException {
        // Sven als Nutzer, id = 1
        var userId = UserEnum.CURRENT_USER.getId();
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));
    }

}
