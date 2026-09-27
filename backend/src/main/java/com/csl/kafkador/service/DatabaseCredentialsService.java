package com.csl.kafkador.service;

import com.csl.kafkador.exception.DatabaseAlreadyConfiguredException;
import com.csl.kafkador.exception.DatabaseSetupException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Properties;

/**
 * First-run setup: Kafkador ships with the embedded H2 database wide open (its default "sa" user,
 * blank password — the same H2 console exposed at /kafkador-h2 can log in with that). This lets an
 * operator replace it, once, with a username and password of their own choosing.
 *
 * <p>The credentials file on disk is the single source of truth for "is setup done" — the database
 * itself can't be asked without already knowing them. {@link com.csl.kafkador.config.DbCredentialsEnvironmentPostProcessor}
 * reads the same file, before the datasource connects, on every later startup.</p>
 */
@Slf4j
@Service("DatabaseCredentialsService")
public class DatabaseCredentialsService {

    private final DataSource dataSource;
    private final Path credentialsFile;

    public DatabaseCredentialsService(DataSource dataSource,
                                       @Value("${kafkador.db-credentials-file:./data/db-credentials.properties}") String credentialsFile) {
        this.dataSource = dataSource;
        this.credentialsFile = Path.of(credentialsFile);
    }

    public boolean isSetupRequired() {
        return !Files.isReadable(credentialsFile);
    }

    public synchronized void configure(String username, String password) throws DatabaseAlreadyConfiguredException, DatabaseSetupException {
        if (!isSetupRequired()) {
            throw new DatabaseAlreadyConfiguredException("Database credentials have already been set.");
        }

        String normalizedUsername = username.toUpperCase(Locale.ROOT);
        String currentUsername = renameDatabaseUser(normalizedUsername, password);
        persistCredentials(username, password);
        log.info("Database credentials configured; embedded H2 user renamed from '{}' to '{}'.", currentUsername, normalizedUsername);
    }

    /** Returns the previous (pre-rename) username, purely for the log line above. */
    private String renameDatabaseUser(String normalizedUsername, String password) throws DatabaseSetupException {
        try (Connection connection = dataSource.getConnection()) {
            String currentUsername = connection.getMetaData().getUserName();
            execute(connection, "ALTER USER " + quoteIdentifier(currentUsername) + " RENAME TO " + quoteIdentifier(normalizedUsername));
            try {
                setPassword(connection, normalizedUsername, password);
            } catch (SQLException e) {
                // Best effort: leave the database reachable under its previous name rather than half-migrated.
                try {
                    execute(connection, "ALTER USER " + quoteIdentifier(normalizedUsername) + " RENAME TO " + quoteIdentifier(currentUsername));
                } catch (SQLException rollbackFailure) {
                    log.error("Failed to roll back the database user rename after the password change failed; " +
                            "the database user is now '{}' with its old password.", normalizedUsername, rollbackFailure);
                }
                throw new DatabaseSetupException("Failed to set the database password.", e);
            }
            return currentUsername;
        } catch (SQLException e) {
            throw new DatabaseSetupException("Failed to configure database credentials.", e);
        }
    }

    private void setPassword(Connection connection, String normalizedUsername, String password) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "ALTER USER " + quoteIdentifier(normalizedUsername) + " SET PASSWORD ?")) {
            statement.setString(1, password);
            statement.execute();
        }
    }

    private void persistCredentials(String username, String password) throws DatabaseSetupException {
        Properties props = new Properties();
        props.setProperty("db.username", username);
        props.setProperty("db.password", password);
        try {
            Path parent = credentialsFile.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = credentialsFile.resolveSibling(credentialsFile.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(out, "Kafkador embedded database credentials. Generated on first run; do not edit while the app is running.");
            }
            restrictToOwner(tmp);
            Files.move(tmp, credentialsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DatabaseSetupException("The database password was changed, but saving the new credentials to disk failed. " +
                    "Kafkador will fail to start on the next restart unless this is fixed manually.", e);
        }
    }

    private void restrictToOwner(Path file) {
        try {
            PosixFileAttributeView view = Files.getFileAttributeView(file, PosixFileAttributeView.class);
            if (view != null) {
                view.setPermissions(PosixFilePermissions.fromString("rw-------"));
            }
            // No POSIX view (Windows): NTFS ACLs aren't set here. The file relies on the OS/filesystem
            // permissions of its containing directory, same as the H2 database files next to it.
        } catch (IOException e) {
            log.warn("Could not restrict permissions on {}: {}", file, e.getMessage());
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    // H2 identifiers: double quotes are the standard-SQL escape, doubled to include a literal quote.
    // Defense in depth alongside DatabaseCredentialsDto's stricter username pattern (letters/digits/underscore only).
    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

}
