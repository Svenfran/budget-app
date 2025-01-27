package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.CreateDataService;
import com.github.svenfran.budgetapp.budgetappbackend.container.TestContainerEnv;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryIT extends TestContainerEnv {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CreateDataService createDataService;


    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void testFindUserById() {
        var loadedUser = createDataService.createUser("Test-User");
        logger.info(loadedUser.toString());
        Optional<User> result = userRepository.findById(loadedUser.getId());
        assertTrue(result.isPresent());

        var user = result.get();

        assertEquals("Test-User", user.getName());
        assertTrue(passwordEncoder.matches("!!Test-User!!", user.getPassword()));
        assertEquals("Test-User@tester.de", user.getEmail());
    }

    @Test
    void testFindUserByEmail() {
        var loadedUser = createDataService.createUser("Test-Alfred");
        logger.info(loadedUser.toString());
        Optional<User> result = userRepository.findByEmail(loadedUser.getEmail());
        assertTrue(result.isPresent());

        var user = result.get();

        assertEquals("Test-Alfred", user.getName());
        assertTrue(passwordEncoder.matches("!!Test-Alfred!!", user.getPassword()));
        assertEquals("Test-Alfred@tester.de", user.getEmail());
    }

    @Test
    void testFindUserByName() {
        var loadedUser = createDataService.createUser("Test-Meier");
        logger.info(loadedUser.toString());
        Optional<User> result = userRepository.findByName(loadedUser.getName());
        assertTrue(result.isPresent());

        var user = result.get();

        assertEquals("Test-Meier", user.getName());
        assertTrue(passwordEncoder.matches("!!Test-Meier!!", user.getPassword()));
        assertEquals("Test-Meier@tester.de", user.getEmail());
    }

    @Test
    void testFindUserByEmailNegative() {
        var loadedUser = createDataService.createUser("Test-Paul");
        logger.info(loadedUser.toString());
        Optional<User> result = userRepository.findByEmail(loadedUser.getEmail());
        assertTrue(result.isPresent());

        var user = result.get();

        assertNotEquals("Test-Alfred", user.getName());
        assertFalse(passwordEncoder.matches("password", user.getPassword()));
        assertNotEquals("alfred@tester.de", user.getEmail());
    }

    @Test
    void testFindUserNotByEmail() {
        var loadedUser = createDataService.createUser("Test-Sven");
        logger.info(loadedUser.toString());
        Optional<User> result = userRepository.findByEmail("sven@tester.de");
        assertFalse(result.isPresent());
    }

}
