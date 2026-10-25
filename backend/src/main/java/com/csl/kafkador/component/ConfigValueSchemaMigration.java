package com.csl.kafkador.component;

import com.csl.kafkador.domain.model.KafkadorConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Widens {@code kafkador_config.config_value} to {@link KafkadorConfig#MAX_VALUE_LENGTH} characters, so a PEM CA chain
 * for Schema Registry, Kafka Connect or ksqlDB fits. Databases created before it hold 2000 characters, and
 * {@code ddl-auto=update} never widens a column.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigValueSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        migrate();
    }

    /** @return whether the column was widened */
    public boolean migrate() {
        try {
            List<Long> lengths = jdbcTemplate.queryForList(
                    "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'KAFKADOR_CONFIG' AND COLUMN_NAME = 'CONFIG_VALUE'",
                    Long.class);
            if (lengths.isEmpty() || lengths.get(0) == null || lengths.get(0) >= KafkadorConfig.MAX_VALUE_LENGTH) return false;
            jdbcTemplate.execute("ALTER TABLE KAFKADOR_CONFIG ALTER COLUMN CONFIG_VALUE SET DATA TYPE VARCHAR(" + KafkadorConfig.MAX_VALUE_LENGTH + ")");
            log.info("Widened kafkador_config.config_value from {} to {} characters", lengths.get(0), KafkadorConfig.MAX_VALUE_LENGTH);
            return true;
        } catch (Exception e) {
            log.warn("Could not widen kafkador_config.config_value; long CA certificates may not save", e);
            return false;
        }
    }

}
