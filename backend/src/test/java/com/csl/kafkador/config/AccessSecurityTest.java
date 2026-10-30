package com.csl.kafkador.config;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sign-in end to end: the setup step that chooses it, the sign-in page's API, CSRF, and LDAP against an in-memory
 * directory. Each test starts from a fresh in-memory database whose credentials are already set, so only "who may use
 * Kafkador" is left to choose.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:access-security-test",
        "kafkador.setup.require-token=false",
        "kafkador.observerService.enabled=false",
        // data.sql is sample data for a file database; this one starts empty.
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccessSecurityTest {

    private static final String DATABASE_ADMIN = "{\"mode\":\"DATABASE\",\"username\":\"admin\",\"password\":\"a-long-password\"}";

    private static InMemoryDirectoryServer ldap;
    private static Path credentialsFile;

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void startDirectory() throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=example,dc=com");
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", 0));
        ldap = new InMemoryDirectoryServer(config);
        ldap.add("dn: dc=example,dc=com", "objectClass: domain", "dc: example");
        ldap.add("dn: ou=people,dc=example,dc=com", "objectClass: organizationalUnit", "ou: people");
        ldap.add("dn: uid=alice,ou=people,dc=example,dc=com", "objectClass: inetOrgPerson", "uid: alice", "cn: Alice",
                "sn: Example", "userPassword: alice-password");
        ldap.startListening();
    }

    @AfterAll
    static void stopDirectory() {
        ldap.shutDown(true);
    }

    @DynamicPropertySource
    static void databaseAlreadySetUp(DynamicPropertyRegistry registry) throws Exception {
        if (credentialsFile == null) {
            credentialsFile = Files.createTempFile("kafkador-db-credentials", ".properties");
            Files.writeString(credentialsFile, "db.username=sa\ndb.password=\n");
            credentialsFile.toFile().deleteOnExit();
        }
        registry.add("kafkador.db-credentials-file", credentialsFile::toString);
    }

    @Test
    void untilSomeoneChoosesTheApiAsksForSetup() throws Exception {
        Browser browser = new Browser();
        browser.perform(get("/api/v1/connections"))
                .andExpect(status().isPreconditionRequired());
        browser.perform(get("/api/v1/setup/status"))
                .andExpect(jsonPath("$.data.accessSetupRequired").value(true))
                .andExpect(jsonPath("$.data.databaseSetupRequired").value(false));
    }

    @Test
    void changesNeedTheTokenFromTheXsrfCookie() throws Exception {
        Browser browser = new Browser();
        browser.perform(get("/api/v1/auth/status"))
                .andExpect(cookie().exists("XSRF-TOKEN"));
        browser.perform(json(post("/api/v1/setup/access"), "{\"mode\":\"NONE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message", containsString("security token")));
    }

    @Test
    void withNoSignInTheApiIsOpenAsBefore() throws Exception {
        Browser browser = new Browser();
        browser.setUp("{\"mode\":\"NONE\"}").andExpect(status().isOk());

        browser.perform(get("/api/v1/connections")).andExpect(status().isOk());
        browser.perform(get("/api/v1/auth/status"))
                .andExpect(jsonPath("$.data.mode").value("NONE"))
                .andExpect(jsonPath("$.data.loginRequired").value(false));
    }

    @Test
    void withADatabaseAccountOnlyTheAdminGetsIn() throws Exception {
        Browser setupBrowser = new Browser();
        setupBrowser.setUp(DATABASE_ADMIN).andExpect(status().isOk());
        // Whoever set it up is signed in already.
        setupBrowser.perform(get("/api/v1/connections")).andExpect(status().isOk());

        Browser browser = new Browser();
        // The connect page's own 401 has no such header; the UI tells the two apart by it.
        browser.perform(get("/api/v1/connections"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(SecurityConfig.LOGIN_REQUIRED_HEADER, "true"));
        browser.login("admin", "wrong-password").andExpect(status().isUnauthorized());
        browser.login("Admin", "a-long-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));
        browser.perform(get("/api/v1/connections")).andExpect(status().isOk());

        browser.perform(post("/api/v1/auth/logout").with(csrf())).andExpect(status().isOk());
        browser.perform(get("/api/v1/connections")).andExpect(status().isUnauthorized());
    }

    @Test
    void signingInGivesTheBrowserANewSessionId() throws Exception {
        new Browser().setUp(DATABASE_ADMIN).andExpect(status().isOk());

        Browser browser = new Browser();
        browser.perform(get("/api/v1/auth/status"));
        Cookie before = browser.session;
        browser.login("admin", "a-long-password").andExpect(status().isOk());

        assertThat(before).isNotNull();
        assertThat(browser.session.getValue()).isNotEqualTo(before.getValue());
    }

    @Test
    void setupCantBeRepeatedOnceChosen() throws Exception {
        new Browser().setUp("{\"mode\":\"NONE\"}").andExpect(status().isOk());

        new Browser().setUp("{\"mode\":\"DATABASE\",\"username\":\"intruder\",\"password\":\"a-long-password\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("Settings")));
    }

    @Test
    void changingWhoMayUseKafkadorSignsEveryoneElseOut() throws Exception {
        Browser admin = new Browser();
        admin.setUp(DATABASE_ADMIN).andExpect(status().isOk());
        Browser other = new Browser();
        other.login("admin", "a-long-password").andExpect(status().isOk());

        admin.perform(json(put("/api/v1/settings/access").with(csrf()),
                        "{\"mode\":\"DATABASE\",\"username\":\"admin\",\"password\":\"another-long-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true));

        admin.perform(get("/api/v1/connections")).andExpect(status().isOk());
        other.perform(get("/api/v1/connections")).andExpect(status().isUnauthorized());
        other.login("admin", "a-long-password").andExpect(status().isUnauthorized());
    }

    @Test
    void settingsNeedASignIn() throws Exception {
        new Browser().setUp(DATABASE_ADMIN).andExpect(status().isOk());

        new Browser().perform(json(put("/api/v1/settings/access").with(csrf()), "{\"mode\":\"NONE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void settingsShowNoPasswords() throws Exception {
        Browser admin = new Browser();
        admin.setUp(DATABASE_ADMIN).andExpect(status().isOk());

        admin.perform(get("/api/v1/settings/access"))
                .andExpect(jsonPath("$.data.mode").value("DATABASE"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void tooManyWrongPasswordsAreRefusedForAWhile() throws Exception {
        new Browser().setUp(DATABASE_ADMIN).andExpect(status().isOk());

        for (int i = 0; i < 5; i++) new Browser().login("admin", "guess-" + i).andExpect(status().isUnauthorized());
        new Browser().login("admin", "a-long-password").andExpect(status().isTooManyRequests());
    }

    @Test
    void ldapSignInChecksTheSettingsWithATestAccountFirst() throws Exception {
        String settings = "\"ldap\":{\"url\":\"ldap://localhost:" + ldap.getListenPort() + "\",\"baseDn\":\"dc=example,dc=com\","
                + "\"userSearchBase\":\"ou=people\",\"userSearchFilter\":\"(uid={0})\"}";

        new Browser().setUp("{\"mode\":\"LDAP\"," + settings + ",\"testUsername\":\"alice\",\"testPassword\":\"wrong\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("didn't accept")));
        new Browser().setUp("{\"mode\":\"LDAP\"," + settings + ",\"testUsername\":\"alice\",\"testPassword\":\"alice-password\"}")
                .andExpect(status().isOk());

        Browser browser = new Browser();
        browser.login("alice", "wrong").andExpect(status().isUnauthorized());
        browser.login("nobody", "alice-password").andExpect(status().isUnauthorized());
        browser.login("alice", "alice-password").andExpect(status().isOk());
        browser.perform(get("/api/v1/connections")).andExpect(status().isOk());
    }

    @Test
    void ldapUserDnPatternWorksToo() throws Exception {
        new Browser().setUp("{\"mode\":\"LDAP\",\"ldap\":{\"url\":\"ldap://localhost:" + ldap.getListenPort() + "\","
                        + "\"baseDn\":\"dc=example,dc=com\",\"userDnPattern\":\"uid={0},ou=people\"},"
                        + "\"testUsername\":\"alice\",\"testPassword\":\"alice-password\"}")
                .andExpect(status().isOk());

        new Browser().login("alice", "alice-password").andExpect(status().isOk());
    }

    /**
     * The Docker healthcheck. Spring Boot would otherwise set up an LDAP connection to localhost:389 that Kafkador never
     * uses, and report DOWN because nothing answers there.
     */
    @Test
    void healthIsUpWithoutAnyLdapServer() throws Exception {
        new Browser().perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void theBrokerAgentStillReachesItsEndpoint() throws Exception {
        new Browser().setUp(DATABASE_ADMIN).andExpect(status().isOk());

        int code = new Browser().perform(json(post("/api/v1/apm/metrics/ingest"), "{}")).andReturn().getResponse().getStatus();
        assertThat(code).isNotIn(401, 403);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Sends the session cookie back, as a browser does: sessions are Spring Session's, found by that cookie. */
    private final class Browser {

        Cookie session;

        ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
            if (session != null) request.cookie(session);
            ResultActions result = mvc.perform(request);
            Cookie returned = result.andReturn().getResponse().getCookie("KAFKADOR_SESSION");
            if (returned != null) session = returned.getMaxAge() == 0 ? null : returned;
            return result;
        }

        ResultActions setUp(String access) throws Exception {
            return perform(json(post("/api/v1/setup/access").with(csrf()), access));
        }

        ResultActions login(String username, String password) throws Exception {
            return perform(json(post("/api/v1/auth/login").with(csrf()),
                    "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
        }

    }

}
