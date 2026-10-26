package com.csl.kafkador.service;

import com.csl.kafkador.component.ClusterCredentialStore;
import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.component.SessionHolder;
import com.csl.kafkador.domain.dto.ClusterCredentialsDto;
import com.csl.kafkador.domain.dto.ClusterSecurityConfigDto;
import com.csl.kafkador.exception.InvalidClusterSecurityException;
import com.csl.kafkador.domain.dto.ConnectionDto;
import com.csl.kafkador.exception.ClusterCredentialsRequiredException;
import com.csl.kafkador.exception.ConnectionSessionExpiredException;
import com.csl.kafkador.domain.model.Cluster;
import com.csl.kafkador.repository.ClusterRepository;
import com.csl.kafkador.service.agent.AgentService;
import jakarta.servlet.http.HttpSession;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionServiceImpTest {

    private static final String CLUSTER_ID = "cluster-1";

    private final ClusterRepository clusterRepository = mock(ClusterRepository.class);
    private final SessionHolder sessionHolder = mock(SessionHolder.class);
    private final HttpSession session = mock(HttpSession.class);
    private final ClusterCredentialStore credentialStore = new ClusterCredentialStore();
    private final ClusterSecurityService securityService = mock(ClusterSecurityService.class);
    private MockedStatic<Admin> adminFactory;
    private ConnectionServiceImp service;

    @BeforeEach
    void setUp() {
        Cluster cluster = new Cluster();
        cluster.setClusterId(CLUSTER_ID);
        cluster.setHost("localhost");
        cluster.setPort("9092");
        when(clusterRepository.findByClusterId(CLUSTER_ID)).thenReturn(Optional.of(cluster));
        when(sessionHolder.getSession()).thenReturn(session);
        adminFactory = mockStatic(Admin.class);
        when(session.getId()).thenReturn("session-1");
        when(securityService.load(any())).thenReturn(ClusterSecurityService.Settings.NONE);
        when(clusterRepository.findById("7")).thenReturn(Optional.of(cluster));
        service = new ConnectionServiceImp(clusterRepository, mock(AgentService.class), sessionHolder,
                mock(SessionSettingsService.class), null, credentialStore, securityService);
    }

    @AfterEach
    void tearDown() {
        adminFactory.close();
    }

    @Test
    void disconnectClosesTheClustersAdminClient() throws Exception {
        Admin admin = mock(Admin.class);
        adminFactory.when(() -> Admin.create(any(Properties.class))).thenReturn(admin);
        service.getAdminClient(CLUSTER_ID);
        when(session.getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString()))
                .thenReturn(new ConnectionDto().setClusterId(CLUSTER_ID));

        service.disconnect();

        verify(admin).close(any(Duration.class));
    }

    @Test
    void unhealthyCachedAdminIsClosedBeforeBeingReplaced() throws Exception {
        Admin broken = adminWithClusterId(failed());
        Admin fresh = mock(Admin.class);
        adminFactory.when(() -> Admin.create(any(Properties.class))).thenReturn(broken, fresh);
        service.getAdminClient(CLUSTER_ID);

        Admin result = service.getAdminClient(CLUSTER_ID).getAdmin();

        assertThat(result).isSameAs(fresh);
        verify(broken).close(any(Duration.class));
    }

    @Test
    void healthyCachedAdminIsReused() throws Exception {
        KafkaFutureImpl<String> ok = new KafkaFutureImpl<>();
        ok.complete(CLUSTER_ID);
        Admin admin = adminWithClusterId(ok);
        adminFactory.when(() -> Admin.create(any(Properties.class))).thenReturn(admin);
        service.getAdminClient(CLUSTER_ID);

        Admin result = service.getAdminClient(CLUSTER_ID).getAdmin();

        assertThat(result).isSameAs(admin);
        verify(admin, never()).close(any(Duration.class));
    }

    @Test
    void trustsAPassedHealthCheckForThirtySecondsInsteadOfCheckingEveryRequest() throws Exception {
        long[] now = {-5_000_000_000L}; // nanoTime may well be negative
        service.clock = () -> now[0];
        Admin admin = adminWithClusterId(completed(CLUSTER_ID));
        adminFactory.when(() -> Admin.create(any(Properties.class))).thenReturn(admin);

        service.getAdminClient(CLUSTER_ID);        // created: not checked yet
        service.getAdminClient(CLUSTER_ID);        // checked
        verify(admin, times(1)).describeCluster();

        now[0] += Duration.ofSeconds(29).toNanos();
        for (int i = 0; i < 5; i++) service.getAdminClient(CLUSTER_ID);
        verify(admin, times(1)).describeCluster();

        now[0] += Duration.ofSeconds(2).toNanos();
        assertThat(service.getAdminClient(CLUSTER_ID).getAdmin()).isSameAs(admin);
        verify(admin, times(2)).describeCluster();
    }

    @Test
    void aNewClientIsCheckedOnItsNextUseWhateverTheClockSays() throws Exception {
        service.clock = () -> -1L;
        Admin broken = adminWithClusterId(failed());
        Admin fresh = mock(Admin.class);
        adminFactory.when(() -> Admin.create(any(Properties.class))).thenReturn(broken, fresh);
        service.getAdminClient(CLUSTER_ID);

        assertThat(service.getAdminClient(CLUSTER_ID).getAdmin()).isSameAs(fresh);
        verify(broken).close(any(Duration.class));
    }

    @Test
    void connectingWithCredentialsAuthenticatesAndRemembersThemForTheSession() throws Exception {
        Admin probe = adminWithClusterId(completed(CLUSTER_ID));
        Admin client = adminWithClusterId(completed(CLUSTER_ID));
        ArgumentCaptor<Properties> properties = ArgumentCaptor.forClass(Properties.class);
        adminFactory.when(() -> Admin.create(properties.capture())).thenReturn(probe, client);

        ConnectionDto connection = service.connect("7", credentials("alice", "s3cr\"et"));
        service.getAdminClient(CLUSTER_ID);

        assertThat(connection.getAuthenticated()).isTrue();
        Properties used = properties.getAllValues().get(1);
        assertThat(used.getProperty("security.protocol")).isEqualTo("SASL_PLAINTEXT");
        assertThat(used.getProperty("sasl.mechanism")).isEqualTo("SCRAM-SHA-512");
        assertThat(used.getProperty("sasl.jaas.config")).isEqualTo(
                "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"alice\" password=\"s3cr\\\"et\";");
        verify(probe).close(any(Duration.class));
    }

    @Test
    void aClusterThatDoesNotAnswerAPlaintextClientAsksForCredentials() {
        Admin probe = adminWithClusterId(failedWith(new TimeoutException("Timed out waiting for a node assignment")));
        adminFactory.when(() -> Admin.create(any(Properties.class))).thenReturn(probe);

        assertThatThrownBy(() -> service.connect("7", null))
                .isInstanceOf(ClusterCredentialsRequiredException.class)
                .hasMessage(ConnectionServiceImp.NO_RESPONSE_MESSAGE);
        verify(session, never()).setAttribute(any(), any());
    }

    @Test
    void rejectedCredentialsSayWhy() {
        Admin probe = adminWithClusterId(failedWith(new SaslAuthenticationException("Authentication failed: Invalid username or password")));
        adminFactory.when(() -> Admin.create(any(Properties.class))).thenReturn(probe);

        assertThatThrownBy(() -> service.connect("7", credentials("alice", "wrong")))
                .isInstanceOf(ClusterCredentialsRequiredException.class)
                .hasMessageContaining("rejected these credentials")
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void anAuthenticatedSessionWhoseCredentialsWereForgottenMustConnectAgain() {
        // e.g. after a backend restart: the session still says it's connected, but the in-memory credentials are gone.
        when(session.getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString()))
                .thenReturn(new ConnectionDto().setClusterId(CLUSTER_ID).setAuthenticated(true));

        assertThatThrownBy(() -> service.getActiveConnection()).isInstanceOf(ConnectionSessionExpiredException.class);

        credentialStore.put("session-1", CLUSTER_ID, credentials("alice", "pw"));
        assertThat(service.getActiveConnection().getClusterId()).isEqualTo(CLUSTER_ID);
    }

    @Test
    void sessionsWithDifferentCredentialsNeverShareAClient() throws Exception {
        Admin anonymous = adminWithClusterId(completed(CLUSTER_ID));
        Admin alice = adminWithClusterId(completed(CLUSTER_ID));
        adminFactory.when(() -> Admin.create(any(Properties.class))).thenReturn(anonymous, alice);

        Admin first = service.getAdminClient(CLUSTER_ID).getAdmin();
        credentialStore.put("session-1", CLUSTER_ID, credentials("alice", "pw"));
        Admin second = service.getAdminClient(CLUSTER_ID).getAdmin();

        assertThat(first).isSameAs(anonymous);
        assertThat(second).isSameAs(alice);
    }

    @Test
    void disconnectForgetsTheCredentialsAndClosesEveryClientOfTheCluster() throws Exception {
        Admin anonymous = adminWithClusterId(completed(CLUSTER_ID));
        Admin alice = adminWithClusterId(completed(CLUSTER_ID));
        adminFactory.when(() -> Admin.create(any(Properties.class))).thenReturn(anonymous, alice);
        service.getAdminClient(CLUSTER_ID);
        credentialStore.put("session-1", CLUSTER_ID, credentials("alice", "pw"));
        service.getAdminClient(CLUSTER_ID);
        when(session.getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString()))
                .thenReturn(new ConnectionDto().setClusterId(CLUSTER_ID));

        service.disconnect();

        verify(anonymous).close(any(Duration.class));
        verify(alice).close(any(Duration.class));
        assertThat(credentialStore.get("session-1", CLUSTER_ID)).isNull();
    }

    @Test
    void securitySettingsTheClusterRejectsAreNotSaved() {
        ClusterSecurityService.Settings candidate = new ClusterSecurityService.Settings(true,
                ClusterSecurityConfigDto.AuthenticationMethod.SASL_PLAIN, "farzin", "wrong", null, "", "");
        when(securityService.candidate(any(), any())).thenReturn(candidate);
        when(session.getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString()))
                .thenReturn(new ConnectionDto().setClusterId(CLUSTER_ID).setHost("localhost").setPort("9092"));
        Admin probe = adminWithClusterId(failedWith(new SaslAuthenticationException("Invalid username or password")));
        adminFactory.when(() -> Admin.create(any(Properties.class))).thenReturn(probe);

        assertThatThrownBy(() -> service.saveSecurityConfig(new ClusterSecurityConfigDto()))
                .isInstanceOf(InvalidClusterSecurityException.class)
                .hasMessageContaining("weren't saved")
                .hasMessageContaining("Invalid username or password");
        verify(securityService, never()).save(any(), any());
    }

    @Test
    void savedCredentialsReplaceTheOnesEnteredForTheSession() throws Exception {
        ClusterSecurityService.Settings candidate = new ClusterSecurityService.Settings(false,
                ClusterSecurityConfigDto.AuthenticationMethod.SASL_PLAIN, "farzin", "pw", null, "", "");
        when(securityService.candidate(any(), any())).thenReturn(candidate);
        ConnectionDto active = new ConnectionDto().setClusterId(CLUSTER_ID).setHost("localhost").setPort("9092").setAuthenticated(true);
        when(session.getAttribute(KafkadorContext.SessionAttribute.ACTIVE_CONNECTION.toString())).thenReturn(active);
        credentialStore.put("session-1", CLUSTER_ID, credentials("alice", "old"));
        Admin probe = adminWithClusterId(completed(CLUSTER_ID));
        adminFactory.when(() -> Admin.create(any(Properties.class))).thenReturn(probe);

        service.saveSecurityConfig(new ClusterSecurityConfigDto());

        verify(securityService).save(CLUSTER_ID, candidate);
        assertThat(credentialStore.get("session-1", CLUSTER_ID)).isNull();
        // The session no longer expects in-memory credentials, so it survives a restart.
        assertThat(active.getAuthenticated()).isFalse();
    }

    @Test
    void aFailedTlsHandshakePointsAtTheTruststoreInsteadOfAskingForCredentials() {
        Exception failure = ConnectionServiceImp.probeFailure(
                new org.apache.kafka.common.errors.SslAuthenticationException("PKIX path building failed"), false);

        assertThat(failure).isInstanceOf(InvalidClusterSecurityException.class).hasMessageContaining("truststore");
    }

    private static ClusterCredentialsDto credentials(String username, String password) {
        return new ClusterCredentialsDto().setSecurityProtocol(ClusterCredentialsDto.SecurityProtocol.SASL_PLAINTEXT)
                .setSaslMechanism(ClusterCredentialsDto.SaslMechanism.SCRAM_SHA_512).setUsername(username).setPassword(password);
    }

    private static KafkaFutureImpl<String> completed(String value) {
        KafkaFutureImpl<String> future = new KafkaFutureImpl<>();
        future.complete(value);
        return future;
    }

    private static KafkaFutureImpl<String> failedWith(Exception failure) {
        KafkaFutureImpl<String> future = new KafkaFutureImpl<>();
        future.completeExceptionally(failure);
        return future;
    }

    private static Admin adminWithClusterId(KafkaFutureImpl<String> clusterId) {
        Admin admin = mock(Admin.class);
        DescribeClusterResult describe = mock(DescribeClusterResult.class);
        when(describe.clusterId()).thenReturn(clusterId);
        when(admin.describeCluster()).thenReturn(describe);
        return admin;
    }

    private static KafkaFutureImpl<String> failed() {
        KafkaFutureImpl<String> future = new KafkaFutureImpl<>();
        future.completeExceptionally(new RuntimeException("broker unreachable"));
        return future;
    }
}
