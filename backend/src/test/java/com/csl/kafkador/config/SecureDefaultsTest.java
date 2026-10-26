package com.csl.kafkador.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** What runs when no profile is chosen stays locked down; the dev profile opens up only what local development needs. */
class SecureDefaultsTest {

    @Test
    void theDefaultsKeepTheH2ConsoleOffNoOtherOriginsAndShortSessions() throws Exception {
        PropertySource<?> defaults = load("application.yml");

        assertThat(defaults.getProperty("spring.h2.console.enabled")).isEqualTo(false);
        assertThat(defaults.getProperty("kafkador.cors.allowed-origins[0]")).isNull();
        assertThat(defaults.getProperty("spring.session.timeout")).isEqualTo("30m");
        assertThat(defaults.getProperty("kafkador.setup.require-token")).isEqualTo(true);
    }

    @Test
    void actuatorShowsOnlyWhetherKafkadorIsHealthy() throws Exception {
        PropertySource<?> defaults = load("application.yml");

        assertThat(defaults.getProperty("management.endpoints.access.default")).isEqualTo("none");
        assertThat(defaults.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(defaults.getProperty("management.endpoint.health.access")).isEqualTo("read-only");
        assertThat(defaults.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(defaults.getProperty("management.endpoint.health.show-components")).isEqualTo("never");
    }

    @Test
    void theDevProfileTurnsOnTheConsoleAndAllowsTheAngularDevServer() throws Exception {
        PropertySource<?> dev = load("application-dev.yml");

        assertThat(dev.getProperty("spring.h2.console.enabled")).isEqualTo(true);
        assertThat(dev.getProperty("kafkador.cors.allowed-origins[0]")).isEqualTo("http://localhost:4200");
        assertThat(dev.getProperty("kafkador.setup.require-token")).isEqualTo(false);
    }

    @Test
    void allowsNoOtherOriginUnlessSomeAreConfigured() {
        ApplicationConfig config = new ApplicationConfig();
        assertThat(corsMappings(config)).isEmpty();

        config.getCors().setAllowedOrigins(List.of("http://localhost:4200"));
        Map<String, CorsConfiguration> mappings = corsMappings(config);
        assertThat(mappings).containsOnlyKeys("/api/**");
        assertThat(mappings.get("/api/**").getAllowedOrigins()).containsExactly("http://localhost:4200");
    }

    private static PropertySource<?> load(String file) throws Exception {
        return new YamlPropertySourceLoader().load(file, new ClassPathResource(file)).get(0);
    }

    private static Map<String, CorsConfiguration> corsMappings(ApplicationConfig config) {
        WebConfig web = new WebConfig(config, null, null);
        Registry registry = new Registry();
        web.corsConfigurer().addCorsMappings(registry);
        return registry.mappings();
    }

    /** Exposes what was registered. */
    private static class Registry extends CorsRegistry {
        Map<String, CorsConfiguration> mappings() {
            return getCorsConfigurations();
        }
    }

}
