package com.github.svenfran.budgetapp.budgetappbackend.dao;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface CategoryRepository extends CrudRepository<Category, Long> {

    List<Category> findAll();
}
