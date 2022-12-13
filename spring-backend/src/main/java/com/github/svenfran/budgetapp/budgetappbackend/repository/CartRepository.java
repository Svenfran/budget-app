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

    List<Cart> findCartsByGroupIdAndIsDeletedFalseOrderByDatePurchasedDesc(Long groupId);

    Optional<Cart> findById(Long id);

    @Override
    void deleteById(Long id);

    @Override
    void deleteAll(Iterable<? extends Cart> entities);

    @Override
    <S extends Cart> S save(S entity);

    @Query("select count(*) from GroupMembershipHistory as gmh where gmh.groupId = :groupId " +
            "and cast(gmh.membershipStart as date) <= :datePurchased " +
            "and (cast(gmh.membershipEnd as date) >= :datePurchased or gmh.membershipEnd IS NULL) group by gmh.groupId")
    int getGroupMemberCountForCartDatePurchased(@Param("datePurchased") Date datePurchased, @Param("groupId") Long groupId);

    @Query("select new com.github.svenfran.budgetapp.budgetappbackend.dto.SpendingsOverviewMonthlyTotalSumAmountDto(year(c.datePurchased) as _year, month(c.datePurchased) as _month, sum(c.amount)) " +
            "from Cart as c where year(c.datePurchased) = :year and c.group.id = :groupId " +
            "group by _month, _year " +
            "order by _month asc")
    List<SpendingsOverviewMonthlyTotalSumAmountDto> getSpendingsMonthlyTotalSumAmountPerMonth(@Param("year") int year, @Param("groupId") Long groupId);

    @Query("select new com.github.svenfran.budgetapp.budgetappbackend.dto.SpendingsOverviewAmountAverageDiffPerMonthDto(" +
            "sum(case when c.user.id = gmh.userId then c.amount else 0 end), sum(c.averagePerMember), sum(case when c.user.id = gmh.userId then c.amount else 0 end) - SUM(c.averagePerMember), " +
            "gmh.userId, year(c.datePurchased) as _year, month(c.datePurchased) as _month) " +
            "from GroupMembershipHistory as gmh join Cart as c on gmh.groupId = c.group.id " +
            "where year(c.datePurchased) = :year and gmh.groupId = :groupId and cast(gmh.membershipStart as date) <= c.datePurchased " +
            "and (cast(gmh.membershipEnd as date) >= c.datePurchased or gmh.membershipEnd IS NULL) " +
            "group by _month, _year, gmh.userId " +
            "order by gmh.userId, _month")
    List<SpendingsOverviewAmountAverageDiffPerMonthDto> getSpendingsAmountAverageDiffPerMonth(@Param("year") int year, @Param("groupId") Long groupId);

    @Query("select new com.github.svenfran.budgetapp.budgetappbackend.dto.SpendingsOverviewAmountAverageDiffPerUserDto(" +
            "sum(case when c.user.id = gmh.userId then c.amount else 0 end), sum(c.averagePerMember), sum(case when c.user.id = gmh.userId then c.amount else 0 end) - SUM(c.averagePerMember), " +
            "gmh.userId, year(c.datePurchased) as _year) " +
            "from GroupMembershipHistory as gmh join Cart as c on gmh.groupId = c.group.id " +
            "where year(c.datePurchased) = :year and gmh.groupId = :groupId and cast(gmh.membershipStart as date) <= c.datePurchased " +
            "and (cast(gmh.membershipEnd as date) >= c.datePurchased or gmh.membershipEnd IS NULL) " +
            "group by _year, gmh.userId " +
            "order by gmh.userId")
    List<SpendingsOverviewAmountAverageDiffPerUserDto> getSpendingsAmountAverageDiffPerUser(@Param("year") int year, @Param("groupId") Long groupId);
}
