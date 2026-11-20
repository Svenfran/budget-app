package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.GroupCurrencyChange;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface GroupCurrencyChangeRepository extends CrudRepository<GroupCurrencyChange, Long> {

    List<GroupCurrencyChange> findByGroupIdOrderByChangedAtAscIdAsc(Long groupId);
}
