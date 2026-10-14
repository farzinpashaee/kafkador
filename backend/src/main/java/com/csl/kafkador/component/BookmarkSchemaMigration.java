package com.csl.kafkador.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keeps {@code bookmark.entity_type} open to new bookmark types. Hibernate pins the column to the enum values
 * known when it creates the table: older databases hold it as a native H2 ENUM of the original three types,
 * newer ones as VARCHAR with a CHECK listing the values of the time. {@code ddl-auto=update} never widens
 * either, so saving a type added later fails. This turns the column into a plain VARCHAR, keeping every
 * stored value; the application only ever writes valid types.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookmarkSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        migrate();
    }

    /** @return whether anything was changed */
    public boolean migrate() {
        try {
            boolean changed = false;
            List<String> types = jdbcTemplate.queryForList(
                    "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'BOOKMARK' AND COLUMN_NAME = 'ENTITY_TYPE'",
                    String.class);
            if (!types.isEmpty() && "ENUM".equalsIgnoreCase(types.get(0))) {
                jdbcTemplate.execute("ALTER TABLE BOOKMARK ALTER COLUMN ENTITY_TYPE SET DATA TYPE VARCHAR(32)");
                changed = true;
            }
            List<String> checks = entityTypeChecks();
            for (String check : checks) {
                jdbcTemplate.execute("ALTER TABLE BOOKMARK DROP CONSTRAINT \"" + check.replace("\"", "\"\"") + "\"");
                changed = true;
            }
            if (changed) log.info("Opened bookmark.entity_type to new bookmark types (plain VARCHAR, no value list)");
            return changed;
        } catch (Exception e) {
            log.warn("Could not open bookmark.entity_type to new bookmark types; bookmarking ksqlDB objects may fail", e);
            return false;
        }
    }

    /** Names of the CHECK constraints on the bookmark table that test entity_type. */
    List<String> entityTypeChecks() {
        return jdbcTemplate.queryForList(
                "SELECT tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                        + "JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc "
                        + "ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME "
                        + "WHERE tc.TABLE_NAME = 'BOOKMARK' AND tc.CONSTRAINT_TYPE = 'CHECK' "
                        + "AND UPPER(cc.CHECK_CLAUSE) LIKE '%ENTITY_TYPE%'",
                String.class);
    }

}
