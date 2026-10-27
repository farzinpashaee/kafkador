package com.csl.kafkador.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The environment variables DOCKER.md documents for presetting who may use Kafkador reach ApplicationConfig.Access. */
class AccessPresetBindingTest {

    @Test
    void bindsTheDocumentedEnvironmentVariables() {
        StandardEnvironment environment = new StandardEnvironment();
        // Spring maps KAFKADOR_ACCESS_LDAP_BASEDN to kafkador.access.ldap.base-dn only in the source of this name.
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of(
                "KAFKADOR_ACCESS_MODE", "ldap",
                "KAFKADOR_ACCESS_ADMINUSERNAME", "admin",
                "KAFKADOR_ACCESS_ADMINPASSWORD", "a-long-password",
                "KAFKADOR_ACCESS_LDAP_URL", "ldaps://ldap.example.com:636",
                "KAFKADOR_ACCESS_LDAP_BASEDN", "dc=example,dc=com",
                "KAFKADOR_ACCESS_LDAP_USERDNPATTERN", "uid={0},ou=people",
                "KAFKADOR_ACCESS_LDAP_USERSEARCHBASE", "ou=people",
                "KAFKADOR_ACCESS_LDAP_USERSEARCHFILTER", "(uid={0})",
                "KAFKADOR_ACCESS_LDAP_MANAGERDN", "cn=kafkador",
                "KAFKADOR_ACCESS_LDAP_MANAGERPASSWORD", "secret")));
        ConfigurationPropertySources.attach(environment);

        ApplicationConfig config = Binder.get(environment).bind("kafkador", ApplicationConfig.class).get();

        ApplicationConfig.Access access = config.getAccess();
        assertThat(access.getMode()).isEqualTo("ldap");
        assertThat(access.getAdminUsername()).isEqualTo("admin");
        assertThat(access.getAdminPassword()).isEqualTo("a-long-password");
        assertThat(access.getLdap().getUrl()).isEqualTo("ldaps://ldap.example.com:636");
        assertThat(access.getLdap().getBaseDn()).isEqualTo("dc=example,dc=com");
        assertThat(access.getLdap().getUserDnPattern()).isEqualTo("uid={0},ou=people");
        assertThat(access.getLdap().getUserSearchBase()).isEqualTo("ou=people");
        assertThat(access.getLdap().getUserSearchFilter()).isEqualTo("(uid={0})");
        assertThat(access.getLdap().getManagerDn()).isEqualTo("cn=kafkador");
        assertThat(access.getLdap().getManagerPassword()).isEqualTo("secret");
    }

}
