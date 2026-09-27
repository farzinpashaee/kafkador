package com.csl.kafkador.service;

import com.csl.kafkador.exception.DatabaseAlreadyConfiguredException;
import com.csl.kafkador.exception.DatabaseSetupException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseCredentialsServiceTest {

    private Path dbDir;
    private String jdbcUrl;
    private Path credentialsFile;
    private HikariDataSource dataSource;
    private DatabaseCredentialsService service;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        dbDir = tempDir;
        jdbcUrl = "jdbc:h2:file:" + dbDir.resolve("kafkadordb");
        credentialsFile = tempDir.resolve("db-credentials.properties");
        dataSource = dataSourceFor("sa", "");
        service = new DatabaseCredentialsService(dataSource, credentialsFile.toString());
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    private HikariDataSource dataSourceFor(String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        return new HikariDataSource(config);
    }

    @Test
    void setupRequired_untilConfigured() {
        assertThat(service.isSetupRequired()).isTrue();

        assertThatCode(() -> service.configure("kafkAdmin", "correct-horse-battery")).doesNotThrowAnyException();

        assertThat(service.isSetupRequired()).isFalse();
        assertThat(Files.exists(credentialsFile)).isTrue();
    }

    @Test
    void configure_rejectsARepeatCall() throws Exception {
        service.configure("kafkAdmin", "correct-horse-battery");

        assertThatThrownBy(() -> service.configure("other", "correct-horse-battery"))
                .isInstanceOf(DatabaseAlreadyConfiguredException.class);
    }

    @Test
    void configure_actuallyChangesTheDatabaseCredentials_oldOnesStopWorking() throws Exception {
        service.configure("kafkAdmin", "correct-horse-battery");

        assertThatThrownBy(() -> DriverManager.getConnection(jdbcUrl, "sa", ""))
                .isInstanceOf(SQLException.class);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "kafkAdmin", "correct-horse-battery")) {
            assertThat(connection.isValid(2)).isTrue();
        }
    }

    @Test
    void savedCredentials_matchWhatTheEnvironmentPostProcessorWouldLoad() throws Exception {
        service.configure("kafkAdmin", "correct-horse-battery");

        Properties props = new Properties();
        try (var in = Files.newInputStream(credentialsFile)) {
            props.load(in);
        }
        assertThat(props.getProperty("db.username")).isEqualToIgnoringCase("kafkAdmin");
        assertThat(props.getProperty("db.password")).isEqualTo("correct-horse-battery");

        // A fresh datasource, as a restarted app would create, must connect with exactly what was saved.
        try (HikariDataSource restarted = dataSourceFor(props.getProperty("db.username"), props.getProperty("db.password"));
             Connection c = restarted.getConnection()) {
            assertThat(c.isValid(2)).isTrue();
        }
    }

    @Test
    void configureFailure_leavesTheDatabaseAndSetupStateUntouched() throws Exception {
        // Pre-create a user with the target name so the RENAME step fails outright.
        try (Connection admin = dataSource.getConnection(); var st = admin.createStatement()) {
            st.execute("CREATE USER \"TAKEN\" PASSWORD 'x'");
        }

        assertThatThrownBy(() -> service.configure("taken", "correct-horse-battery"))
                .isInstanceOf(DatabaseSetupException.class);

        // sa must still work: a failed configure() must not leave the database half-migrated, and
        // setup must still be considered outstanding.
        try (Connection c = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            assertThat(c.isValid(2)).isTrue();
        }
        assertThat(service.isSetupRequired()).isTrue();
    }

    @Test
    void credentialsFile_isNotWorldReadable_wherePosixPermissionsApply() throws Exception {
        service.configure("kafkAdmin", "correct-horse-battery");

        if (Files.getFileAttributeView(credentialsFile, PosixFileAttributeView.class) != null) {
            assertThat(Files.getPosixFilePermissions(credentialsFile))
                    .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        }
    }

}
