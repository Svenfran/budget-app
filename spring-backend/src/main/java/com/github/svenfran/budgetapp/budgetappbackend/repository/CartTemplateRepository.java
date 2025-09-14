package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.CartTemplate;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface CartTemplateRepository extends CrudRepository<CartTemplate, Long> {
    List<CartTemplate> findByActiveTrue();

    List<CartTemplate> findByGroupId(Long groupId);

    List<CartTemplate> findByUserId(Long userId);

    List<CartTemplate> findByUserIdAndGroupIdAndActiveTrue(Long userId, Long groupId);
}
