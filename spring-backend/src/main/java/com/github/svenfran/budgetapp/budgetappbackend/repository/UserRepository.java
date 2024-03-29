package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserRepository extends CrudRepository<User, Long> {

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByName(String userName);

}
