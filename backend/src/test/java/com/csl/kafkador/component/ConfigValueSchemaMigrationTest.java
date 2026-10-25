package com.csl.kafkador.component;

import com.csl.kafkador.domain.model.KafkadorConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import(ConfigValueSchemaMigration.class)
class ConfigValueSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ConfigValueSchemaMigration migration;

    @Test
    void widensAnOldColumnKeepingItsRowsSoACaChainFits() {
        // How databases created before CA certificates hold the column.
        jdbcTemplate.execute("ALTER TABLE KAFKADOR_CONFIG ALTER COLUMN CONFIG_VALUE SET DATA TYPE VARCHAR(2000)");
        jdbcTemplate.update("INSERT INTO KAFKADOR_CONFIG (CLUSTER_ID, CONFIG_KEY, CONFIG_VALUE) VALUES ('c1', 'kafkador.ksql-db.url', 'http://ksql:8088')");

        assertThat(migration.migrate()).isTrue();

        assertThat(columnLength()).isEqualTo(KafkadorConfig.MAX_VALUE_LENGTH);
        assertThat(jdbcTemplate.queryForObject("SELECT CONFIG_VALUE FROM KAFKADOR_CONFIG WHERE CONFIG_KEY = 'kafkador.ksql-db.url'", String.class))
                .isEqualTo("http://ksql:8088");
        assertThatCode(() -> jdbcTemplate.update("INSERT INTO KAFKADOR_CONFIG (CLUSTER_ID, CONFIG_KEY, CONFIG_VALUE) VALUES (?, ?, ?)",
                "c1", "kafkador.ksql-db.tls.ca-certificate", "x".repeat(10_000))).doesNotThrowAnyException();
        assertThat(migration.migrate()).isFalse();
    }

    private long columnLength() {
        return jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'KAFKADOR_CONFIG' AND COLUMN_NAME = 'CONFIG_VALUE'",
                Long.class);
    }

}
