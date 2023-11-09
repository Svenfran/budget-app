package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.container.TestContainerEnv;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TokenRepositoryIT extends TestContainerEnv {

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findAllValidTokensByUser() {
    }

    @Test
    void findByToken() {
    }

    @Test
    void findAllByUserId() {
    }
}
