package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.CreateDataService;
import com.github.svenfran.budgetapp.budgetappbackend.container.TestContainerEnv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenRepositoryIT extends TestContainerEnv {

    private final String jwt1 = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJTdmVuLmhhaWRlckB3ZWIuZGUiLCJpYXQiOjE2OTkxMTg1NDksImV4cCI6MTcwMDkzMjk0OX0.Fra488FbW3095p6EQXk7WFXKrBjfHTNgMKm1oTPJHAo";
    private final String jwt2 = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwYXVsQHRlc3QuY29tIiwiaWF0IjoxNjk5MTExOTA1LCJleHAiOjE3MDA5MjYzMDV9._CsPIEn8cdfB4Hpphn0Ny4WnsDjrlKZP5wPiTDpPbL0";
    private final String jwt3 = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJTdmVuLmhhaWRlckB3ZWIuZGUiLCJpYXQiOjE2OTk0MjcxODYsImV4cCI6MTcwMTI0MTU4Nn0.XXOiQXBCurkscxlI0qpYPUe2rMGklnK25oYt74imlV8";


    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreateDataService createDataService;


    @AfterEach
    void cleanUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @Transactional
    void findAllValidTokensByUser() {
        var user = createDataService.createUser("QM-User");
        createDataService.createTokenForUser(jwt1, user, false, false);
        createDataService.createTokenForUser(jwt2, user, false, false);

        var tokens = tokenRepository.findAllValidTokensByUser(user.getId());
        assertEquals(2, tokens.size());
    }

    @Test
    @Transactional
    void findByToken() {
        var user = createDataService.createUser("Super-User");
        createDataService.createTokenForUser(jwt1, user, false, false);
        createDataService.createTokenForUser(jwt2, user, false, false);
        createDataService.createTokenForUser(jwt3, user, false, false);

        var loadedToken1 = tokenRepository.findByToken(jwt1);
        var loadedToken2 = tokenRepository.findByToken(jwt2);
        var loadedToken3 = tokenRepository.findByToken(jwt3);

        assertTrue(loadedToken1.isPresent());
        assertTrue(loadedToken2.isPresent());
        assertTrue(loadedToken3.isPresent());

        assertEquals(jwt1, loadedToken1.get().getToken());
        assertEquals(jwt2, loadedToken2.get().getToken());
        assertEquals(jwt3, loadedToken3.get().getToken());
    }

    @Test
    void findAllByUserId() {
        var user = createDataService.createUser("Test-User");
        createDataService.createTokenForUser(jwt1, user, false, false);
        createDataService.createTokenForUser(jwt2, user, false, false);
        createDataService.createTokenForUser(jwt3, user, false, false);

        var tokens = tokenRepository.findAllByUserId(user.getId());
        assertEquals(3, tokens.size());
    }
}
