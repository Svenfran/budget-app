package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.GroupMembershipHistory;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface GroupMembershipHistoryRepository extends CrudRepository<GroupMembershipHistory, Long> {

    GroupMembershipHistory findByUserIdAndGroupIdAndMembershipEndIsNull(Long userId, Long groupId);

    List<GroupMembershipHistory> findByGroupId(Long groupId);

    List<GroupMembershipHistory> findByGroupIdAndUserId(Long groupId, Long userId);

    List<GroupMembershipHistory> findByGroupIdAndMembershipEndIsNull(Long groupId);

    List<GroupMembershipHistory> findAll();

    @Override
    void deleteAll();
}
