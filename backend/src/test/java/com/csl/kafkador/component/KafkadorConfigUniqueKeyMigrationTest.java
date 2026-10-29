package com.csl.kafkador.component;

import com.csl.kafkador.repository.KafkadorConfigRepository;
import com.csl.kafkador.service.config.GenericKafkadorConfigServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import({KafkadorConfigUniqueKeyMigration.class, GenericKafkadorConfigServiceImp.class})
class KafkadorConfigUniqueKeyMigrationTest {

    private static final String INSERT = "INSERT INTO KAFKADOR_CONFIG (CLUSTER_ID, CONFIG_KEY, CONFIG_VALUE) VALUES (?, ?, ?)";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private KafkadorConfigUniqueKeyMigration migration;
    @Autowired
    private GenericKafkadorConfigServiceImp configService;
    @Autowired
    private KafkadorConfigRepository repository;

    /** Like a database from before the constraint: the tests share one, and a constraint isn't rolled back. */
    @BeforeEach
    void withoutTheConstraint() {
        jdbcTemplate.execute("ALTER TABLE KAFKADOR_CONFIG DROP CONSTRAINT IF EXISTS " + KafkadorConfigUniqueKeyMigration.CONSTRAINT);
    }

    @Test
    void readsStillWorkWhileDuplicatesExist() throws Exception {
        jdbcTemplate.update(INSERT, "c1", "kafkador.schema-registry.url", "http://old:8081");
        jdbcTemplate.update(INSERT, "c1", "kafkador.schema-registry.url", "http://new:8081");

        assertThat(configService.get("kafkador.schema-registry.url", "c1")).isEqualTo("http://new:8081");
        assertThat(configService.get("c1").get("kafkador.schema-registry.url").getConfigValue()).isEqualTo("http://new:8081");
    }

    @Test
    void keepsTheNewestOfEachDuplicateAndPreventsMore() {
        // What two Schema Registry calls discovering its URL at the same moment left behind.
        jdbcTemplate.update(INSERT, "c1", "kafkador.schema-registry.url", "http://old:8081");
        jdbcTemplate.update(INSERT, "c1", "kafkador.schema-registry.url", "http://new:8081");
        jdbcTemplate.update(INSERT, "c2", "kafkador.schema-registry.url", "http://other:8081");
        jdbcTemplate.update(INSERT, "global", "kafkador.session.timeout-minutes", "30");

        assertThat(migration.migrate()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForList("SELECT CONFIG_VALUE FROM KAFKADOR_CONFIG WHERE CLUSTER_ID = 'c1'", String.class))
                .containsExactly("http://new:8081");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM KAFKADOR_CONFIG", Integer.class)).isEqualTo(3);
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT, "c1", "kafkador.schema-registry.url", "http://again:8081"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(migration.migrate()).as("runs again at every start").isZero();
    }

    @Test
    void concurrentSavesOfOneKeyKeepOneRow() throws Exception {
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            String value = "http://host-" + i + ":8081";
            threads[i] = new Thread(() -> configService.save(java.util.Map.entry("kafkador.schema-registry.url", value), "c1"));
        }
        for (Thread thread : threads) thread.start();
        for (Thread thread : threads) thread.join();

        assertThat(repository.findByClusterId("c1")).hasSize(1);
    }

}
