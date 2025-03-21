package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface CartRepository extends CrudRepository<Cart, Long> {

    List<Cart> findCartsByGroupAndUser(Group group, User user);

    List<Cart> findCartsByGroupIdOrderByDatePurchasedDesc(Long groupId);

    Optional<Cart> findById(Long id);

    @Override
    void deleteById(Long id);

    @Override
    void deleteAll(Iterable<? extends Cart> entities);

    @Override
    <S extends Cart> S save(S entity);

    @Query("""
            select count(distinct gmh.userId) from GroupMembershipHistory as gmh where gmh.groupId = :groupId
            and cast(gmh.membershipStart as date) <= :datePurchased
            and ((cast(gmh.membershipEnd as date) >= :datePurchased) or gmh.membershipEnd IS NULL)
            group by gmh.groupId
            """)
    int getGroupMemberCountForCartDatePurchased(@Param("datePurchased") Date datePurchased, @Param("groupId") Long groupId);

    @Query("""
            select year(c.datePurchased) as _year from Cart as c where c.group.id = :groupId
            group by _year 
            order by _year desc
            """)
    List<Integer> getAvailableYearsForGroup(@Param("groupId") Long groupId);

}
