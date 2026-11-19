package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends CrudRepository<Category, Long> {

    List<Category> findAll();

    List<Category> findAllByGroup_IdOrderByName(Long groupId);

    Optional<Category> findById(Long id);

    /** Settlement-Kategorie der Gruppe (DIV-74); bei mehreren gekennzeichneten die mit der kleinsten Id. */
    Optional<Category> findFirstByGroupAndSettlementTrueOrderByIdAsc(Group group);

    Category save (Category category);

    void deleteById(Long id);
}
