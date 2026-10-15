package com.csl.kafkador.util;

import com.csl.kafkador.exception.KsqlStatementException;
import com.csl.kafkador.util.KsqlStatements.SourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KsqlStatementsTest {

    @Test
    void acceptsCreateStatementsOfTheRequestedType() throws Exception {
        assertThat(KsqlStatements.requireCreate(SourceType.STREAM,
                "CREATE STREAM orders (id VARCHAR KEY) WITH (KAFKA_TOPIC='orders', VALUE_FORMAT='JSON')"))
                .endsWith("VALUE_FORMAT='JSON');");
        assertThat(KsqlStatements.requireCreate(SourceType.TABLE, "  create or replace table totals as select * from t;  "))
                .isEqualTo("create or replace table totals as select * from t;");
        assertThat(KsqlStatements.requireCreate(SourceType.STREAM, "-- my stream\nCREATE SOURCE STREAM IF NOT EXISTS s WITH (KAFKA_TOPIC='s');"))
                .startsWith("-- my stream");
    }

    @Test
    void rejectsOtherStatementsWhenCreating() {
        assertThatThrownBy(() -> KsqlStatements.requireCreate(SourceType.STREAM, "CREATE TABLE t (id INT PRIMARY KEY) WITH (KAFKA_TOPIC='t');"))
                .isInstanceOf(KsqlStatementException.class).hasMessage("Enter a CREATE STREAM statement");
        assertThatThrownBy(() -> KsqlStatements.requireCreate(SourceType.STREAM, "DROP STREAM s;"))
                .hasMessage("Enter a CREATE STREAM statement");
        assertThatThrownBy(() -> KsqlStatements.requireCreate(SourceType.STREAM, "  ;  "))
                .hasMessage("Enter a statement");
    }

    @Test
    void rejectsMoreThanOneStatement() {
        assertThatThrownBy(() -> KsqlStatements.requireCreate(SourceType.STREAM,
                "CREATE STREAM s WITH (KAFKA_TOPIC='s'); DROP TABLE t;"))
                .hasMessage("Enter one statement at a time");
    }

    @Test
    void ignoresSemicolonsInStringsAndComments() throws Exception {
        String statement = "CREATE STREAM s WITH (KAFKA_TOPIC='a;b', VALUE_DELIMITER=';') -- done; really\n";
        assertThat(KsqlStatements.requireCreate(SourceType.STREAM, statement)).isEqualTo(statement.strip() + ";");
        assertThat(KsqlStatements.requireCreate(SourceType.STREAM, "/* ; */ CREATE STREAM s WITH (KAFKA_TOPIC='it''s;');"))
                .isNotBlank();
    }

    @Test
    void acceptsReplacingOrAlteringTheNamedSource() throws Exception {
        assertThat(KsqlStatements.requireUpdate(SourceType.STREAM, "ORDERS", "CREATE OR REPLACE STREAM orders (id INT KEY) WITH (KAFKA_TOPIC='o');"))
                .isNotBlank();
        assertThat(KsqlStatements.requireUpdate(SourceType.TABLE, "Totals", "ALTER TABLE `Totals` ADD COLUMN extra INT;"))
                .isNotBlank();
    }

    @Test
    void rejectsUpdatesOfAnotherSourceOrWithoutReplace() {
        assertThatThrownBy(() -> KsqlStatements.requireUpdate(SourceType.STREAM, "ORDERS", "CREATE OR REPLACE STREAM payments WITH (KAFKA_TOPIC='p');"))
                .hasMessage("The statement must change stream ORDERS");
        assertThatThrownBy(() -> KsqlStatements.requireUpdate(SourceType.STREAM, "ORDERS", "CREATE STREAM orders WITH (KAFKA_TOPIC='o');"))
                .hasMessage("Enter a CREATE OR REPLACE STREAM or ALTER STREAM statement");
        assertThatThrownBy(() -> KsqlStatements.requireUpdate(SourceType.STREAM, "Orders", "ALTER STREAM orders ADD COLUMN x INT;"))
                .as("a bare name is upper-cased, so it doesn't match a case-sensitive one")
                .hasMessage("The statement must change stream Orders");
        assertThatThrownBy(() -> KsqlStatements.requireUpdate(SourceType.TABLE, "ORDERS", "ALTER STREAM orders ADD COLUMN x INT;"))
                .hasMessage("The statement must change table ORDERS");
    }

    @Test
    void quotesIdentifiersAndRejectsBackticks() throws Exception {
        assertThat(KsqlStatements.quoteIdentifier("ORDERPQ")).isEqualTo("`ORDERPQ`");
        assertThatThrownBy(() -> KsqlStatements.quoteIdentifier("a`; DROP STREAM b; --"))
                .isInstanceOf(KsqlStatementException.class);
    }

}
