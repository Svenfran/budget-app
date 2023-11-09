package com.github.svenfran.budgetapp.budgetappbackend.repository;

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


    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void findById() {
        var loadedUser = loadUser("Test-User", "querz123", "tom@tester.com");
        logger.info(loadedUser.toString());
        Optional<User> result = userRepository.findById(loadedUser.getId());
        assertTrue(result.isPresent());

        var user = result.get();

        assertEquals("Test-User", user.getName());
        assertTrue(passwordEncoder.matches("querz123", user.getPassword()));
        assertEquals("tom@tester.com", user.getEmail());
    }

    @Test
    void findByEmail() {
        var loadedUser = loadUser("Test-Alfred", "password", "alfred@tester.com");
        logger.info(loadedUser.toString());
        Optional<User> result = userRepository.findByEmail(loadedUser.getEmail());
        assertTrue(result.isPresent());

        var user = result.get();

        assertEquals("Test-Alfred", user.getName());
        assertTrue(passwordEncoder.matches("password", user.getPassword()));
        assertEquals("alfred@tester.com", user.getEmail());
    }

    @Test
    void findByName() {
        var loadedUser = loadUser("Test-Meier", "123456", "meier@tester.com");
        logger.info(loadedUser.toString());
        Optional<User> result = userRepository.findByName(loadedUser.getName());
        assertTrue(result.isPresent());

        var user = result.get();

        assertEquals("Test-Meier", user.getName());
        assertTrue(passwordEncoder.matches("123456", user.getPassword()));
        assertEquals("meier@tester.com", user.getEmail());
    }

    @Test
    void findByEmailNegative() {
        var loadedUser = loadUser("Test-Paul", "kdSWk12l", "paul@tester.com");
        logger.info(loadedUser.toString());
        Optional<User> result = userRepository.findByEmail(loadedUser.getEmail());
        assertTrue(result.isPresent());

        var user = result.get();

        assertNotEquals("Test-Alfred", user.getName());
        assertFalse(passwordEncoder.matches("password", user.getPassword()));
        assertNotEquals("alfred@tester.com", user.getEmail());
    }

    @Test
    void findNotByEmail() {
        var loadedUser = loadUser("Test-Sven", "kdJF5§§", "sven@tester.com");
        logger.info(loadedUser.toString());
        Optional<User> result = userRepository.findByEmail("sven@tester.de");
        assertFalse(result.isPresent());
    }

    private User loadUser(String name, String password, String email) {
        User user = new User();
        user.setName(name);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        return userRepository.save(user);
    }

}
