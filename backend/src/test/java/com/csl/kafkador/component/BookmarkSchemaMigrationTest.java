package com.csl.kafkador.component;

import com.csl.kafkador.domain.model.Bookmark;
import com.csl.kafkador.domain.options.BookmarkEntityType;
import com.csl.kafkador.repository.BookmarkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import(BookmarkSchemaMigration.class)
class BookmarkSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private BookmarkRepository repository;
    @Autowired
    private BookmarkSchemaMigration migration;

    // H2 commits DDL at once, so each test sets up the schema it starts from rather than relying on test order.

    @Test
    void dropsTheValueListHibernatePutsOnANewTable() {
        // How Hibernate creates the column on a new database.
        migration.migrate();
        jdbcTemplate.execute("ALTER TABLE BOOKMARK ADD CHECK (ENTITY_TYPE IN ('TOPIC','BROKER','CONSUMER_GROUP'))");
        assertThat(entityTypeChecks()).hasSize(1);

        assertThat(migration.migrate()).isTrue();

        assertThat(columnType()).isEqualTo("CHARACTER VARYING");
        assertThat(entityTypeChecks()).isEmpty();
        assertThat(migration.migrate()).isFalse();
        // A type added after the table was created can be stored.
        assertThatCode(() -> jdbcTemplate.update(
                "INSERT INTO BOOKMARK (ID, CLUSTER_ID, ENTITY_TYPE, ENTITY_ID) VALUES ('x', 'c1', 'SOME_FUTURE_TYPE', 'e')"))
                .doesNotThrowAnyException();
    }

    @Test
    void convertsAnOldEnumColumnKeepingItsRowsAndAcceptingNewTypes() {
        // How databases created before the ksqlDB types hold the column.
        migration.migrate();
        jdbcTemplate.execute("ALTER TABLE BOOKMARK ALTER COLUMN ENTITY_TYPE SET DATA TYPE ENUM('TOPIC','BROKER','CONSUMER_GROUP')");
        repository.saveAndFlush(bookmark(BookmarkEntityType.TOPIC, "orders"));
        assertThat(columnType()).isEqualTo("ENUM");

        assertThat(migration.migrate()).isTrue();

        assertThat(columnType()).isEqualTo("CHARACTER VARYING");
        repository.saveAndFlush(bookmark(BookmarkEntityType.KSQL_STREAM, "ORDERPQ"));
        assertThat(repository.findByClusterIdAndEntityTypeOrderByCreateDateTimeDesc("c1", BookmarkEntityType.TOPIC))
                .extracting(Bookmark::getEntityId).containsExactly("orders");
        assertThat(repository.findByClusterIdAndEntityTypeOrderByCreateDateTimeDesc("c1", BookmarkEntityType.KSQL_STREAM))
                .extracting(Bookmark::getEntityId).containsExactly("ORDERPQ");
    }

    private String columnType() {
        return jdbcTemplate.queryForObject(
                "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'BOOKMARK' AND COLUMN_NAME = 'ENTITY_TYPE'",
                String.class);
    }

    private List<String> entityTypeChecks() {
        return migration.entityTypeChecks();
    }

    private static Bookmark bookmark(BookmarkEntityType type, String id) {
        Bookmark bookmark = new Bookmark();
        bookmark.setId(UUID.randomUUID().toString());
        bookmark.setClusterId("c1");
        bookmark.setEntityType(type);
        bookmark.setEntityId(id);
        bookmark.setCreateDateTime(new Date());
        return bookmark;
    }

}
