package com.csl.kafkador.service;

import com.csl.kafkador.component.KafkadorContext;
import com.csl.kafkador.component.SessionHolder;
import com.csl.kafkador.domain.dto.ConnectionDto;
import com.csl.kafkador.domain.model.Cluster;
import com.csl.kafkador.repository.ClusterRepository;
import com.csl.kafkador.service.agent.AgentService;
import jakarta.servlet.http.HttpSession;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionServiceImpTest {

    private static final String CLUSTER_ID = "cluster-1";

    private final ClusterRepository clusterRepository = mock(ClusterRepository.class);
    private final SessionHolder sessionHolder = mock(SessionHolder.class);
    private final HttpSession session = mock(HttpSession.class);
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
        service = new ConnectionServiceImp(clusterRepository, mock(AgentService.class), sessionHolder,
                mock(SessionSettingsService.class), null);
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
