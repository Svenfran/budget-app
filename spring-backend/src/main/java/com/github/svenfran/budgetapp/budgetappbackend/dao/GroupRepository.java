package com.github.svenfran.budgetapp.budgetappbackend.dao;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface GroupRepository extends CrudRepository<Group, Long> {

    List<Group> findGroupsByMembersInOrderById(Set<User> members);

    List<Group> findGroupsByOwnerOrderById(User owner);

    Optional<Group> findById(Long id);

    Group save(Group group);
}
