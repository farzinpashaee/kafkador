package com.csl.kafkador.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One row per setting: removes rows of kafkador_config that repeat a (cluster, key) pair, keeping the newest, then adds
 * a unique constraint so it can't happen again. Before saves were locked (GenericKafkadorConfigServiceImp), requests
 * that discovered a Schema Registry, Connect or ksqlDB URL at the same moment each saved it, and every later read of
 * that cluster's settings failed on the duplicate key.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkadorConfigUniqueKeyMigration implements ApplicationRunner {

    static final String CONSTRAINT = "UK_KAFKADOR_CONFIG_CLUSTER_KEY";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        migrate();
    }

    /** @return how many duplicate rows were removed */
    public int migrate() {
        try {
            int removed = jdbcTemplate.update("""
                    DELETE FROM KAFKADOR_CONFIG c WHERE EXISTS (
                        SELECT 1 FROM KAFKADOR_CONFIG newer
                        WHERE newer.CONFIG_KEY = c.CONFIG_KEY
                          AND newer.CLUSTER_ID IS NOT DISTINCT FROM c.CLUSTER_ID
                          AND newer.ID > c.ID)""");
            if (removed > 0) log.info("Removed {} duplicate settings from kafkador_config, keeping the newest of each", removed);
            jdbcTemplate.execute("ALTER TABLE KAFKADOR_CONFIG ADD CONSTRAINT IF NOT EXISTS " + CONSTRAINT
                    + " UNIQUE (CLUSTER_ID, CONFIG_KEY)");
            return removed;
        } catch (Exception e) {
            log.warn("Could not remove duplicate settings from kafkador_config", e);
            return 0;
        }
    }

}
