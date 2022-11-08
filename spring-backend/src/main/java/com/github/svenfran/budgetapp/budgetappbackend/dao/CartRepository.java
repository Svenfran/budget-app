package com.github.svenfran.budgetapp.budgetappbackend.dao;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import org.springframework.data.repository.CrudRepository;

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
}
