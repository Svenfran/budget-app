package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface GroupRepository extends CrudRepository<Group, Long> {

    List<Group> findGroupsByMembersInOrderById(Set<User> members);

    List<Group> findGroupsByOwnerOrderById(User owner);

    Optional<Group> findById(Long id);

    Group save(Group group);

    // Direktes UPDATE statt save(group): sperrt die Gruppenzeile sofort (nicht erst beim Flush) und serialisiert so
    // Anlagen und Umsortierungen der Einkaufslisten einer Gruppe
    @Modifying
    @Query("update Group g set g.lastUpdateShoppingList = :now where g.id = :groupId")
    void touchShoppingLists(@Param("groupId") Long groupId, @Param("now") Date now);

    @Query("select g.listOrderRevision from Group g where g.id = :groupId")
    long findListOrderRevision(@Param("groupId") Long groupId);
}
