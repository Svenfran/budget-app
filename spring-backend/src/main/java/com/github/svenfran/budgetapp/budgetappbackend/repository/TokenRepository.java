package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Token;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TokenRepository extends CrudRepository<Token, Long> {

    @Query("""
    select t from Token t inner join User u on t.user.id = u.id
    where u.id = :userId and (t.expired = false or t.revoked = false)
    """)
    List<Token> findAllValidTokensByUser(@Param("userId") Long userId);

    @Query("""
    select t from Token t inner join User u on t.user.id = u.id
    where u.id = :userId and (t.expired = false or t.revoked = false)
    and t.deviceId = :deviceId
    """)
    List<Token> findAllValidTokensByUserAndDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    List<Token> findAllByExpiredIsTrueAndRevokedIsTrue();

    @Query("""
    select t from Token t 
    where t.user.id = :userId and (t.expired = true or t.revoked = true)
    and t.deviceId = :deviceId
    """)
    List<Token> findAllInvalidTokensByUserAndDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    Optional<Token> findByToken(String token);

    List<Token> findAllByUserId(Long userId);
}
