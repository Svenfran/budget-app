package com.github.svenfran.budgetapp.budgetappbackend.testsupport

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Leert alle Tabellen außer den Liquibase-Tabellen. Nur für Commit-Specs
 * (propagation = NOT_SUPPORTED), dort in setup() vor jedem Feature aufrufen.
 */
@Component
class DatabaseCleaner {

    @Autowired JdbcTemplate jdbcTemplate

    void truncateAll() {
        def tables = jdbcTemplate.queryForList(
                "select tablename from pg_tables where schemaname = 'public' and tablename not like 'databasechangelog%'",
                String)
        jdbcTemplate.execute("TRUNCATE TABLE " + tables.collect { '"' + it + '"' }.join(', ') + " RESTART IDENTITY CASCADE")
    }
}
