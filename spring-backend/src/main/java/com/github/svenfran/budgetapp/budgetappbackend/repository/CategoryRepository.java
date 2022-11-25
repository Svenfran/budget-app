package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends CrudRepository<Category, Long> {

    List<Category> findAll();

    List<Category> findAllByGroup_IdOrderByName(Long groupId);

    Optional<Category> findById(Long id);

    Category findCategoryByName(String name);

    Category save (Category category);

    void deleteById(Long id);
}
