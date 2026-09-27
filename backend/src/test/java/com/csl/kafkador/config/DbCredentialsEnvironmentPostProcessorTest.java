package com.csl.kafkador.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DbCredentialsEnvironmentPostProcessorTest {

    private final DbCredentialsEnvironmentPostProcessor processor = new DbCredentialsEnvironmentPostProcessor();

    @Test
    void noCredentialsFile_leavesDatasourcePropertiesUntouched(@TempDir Path dir) {
        MockEnvironment environment = environmentPointingAt(dir.resolve("missing.properties"));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.username")).isNull();
        assertThat(environment.getProperty("spring.datasource.password")).isNull();
    }

    @Test
    void credentialsFilePresent_overridesDatasourceUsernameAndPassword(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("db-credentials.properties");
        Files.writeString(file, "db.username=KAFKADMIN\ndb.password=s3cret\n");
        MockEnvironment environment = environmentPointingAt(file);

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("KAFKADMIN");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("s3cret");
    }

    @Test
    void malformedCredentialsFile_isIgnoredRatherThanFailingStartup(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("db-credentials.properties");
        Files.writeString(file, "db.username=\n"); // no password at all
        MockEnvironment environment = environmentPointingAt(file);

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.username")).isNull();
    }

    @Test
    void order_runsLateEnoughToOverrideConfigFileValues() {
        assertThat(processor.getOrder()).isGreaterThan(0);
    }

    private static MockEnvironment environmentPointingAt(Path file) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(DbCredentialsEnvironmentPostProcessor.CREDENTIALS_FILE_PROPERTY, file.toString());
        return environment;
    }

}
