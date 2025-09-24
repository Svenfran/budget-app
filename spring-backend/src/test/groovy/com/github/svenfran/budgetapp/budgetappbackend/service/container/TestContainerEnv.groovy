package com.github.svenfran.budgetapp.budgetappbackend.service.container

import com.github.svenfran.budgetapp.budgetappbackend.BudgetAppApplication
import org.jetbrains.annotations.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import spock.lang.Specification

@SpringBootTest(classes = BudgetAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
abstract class TestContainerEnv extends Specification {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
            .withReuse(true);


    static { postgres.start() }

    @DynamicPropertySource
    static void configureProperties(@NotNull DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class.name", postgres::getDriverClassName);
    }

}
