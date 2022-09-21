package com.github.svenfran.budgetapp.budgetappbackend.dao;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends CrudRepository<Category, Long> {

    List<Category> findAll();

    Optional<Category> findById(Long id);

    Category findCategoryByName(String name);

    Category save (Category category);

    void deleteById(Long id);
}
