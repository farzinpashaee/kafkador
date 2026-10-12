package com.csl.kafkador.component;

import com.csl.kafkador.domain.dto.ClusterCredentialsDto;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cluster credentials per HTTP session, kept in memory only. HTTP sessions are persisted to the database
 * (Spring Session JDBC), so storing passwords as session attributes would write them to disk. The cost is
 * that a backend restart forgets them and users connect, entering their credentials, again.
 */
@Component
public class ClusterCredentialStore {

    private final Map<String, Map<String, ClusterCredentialsDto>> bySession = new ConcurrentHashMap<>();

    public ClusterCredentialsDto get(String sessionId, String clusterId) {
        if (sessionId == null || clusterId == null) return null;
        Map<String, ClusterCredentialsDto> clusters = bySession.get(sessionId);
        return clusters == null ? null : clusters.get(clusterId);
    }

    public void put(String sessionId, String clusterId, ClusterCredentialsDto credentials) {
        if (sessionId == null || clusterId == null || credentials == null) return;
        bySession.computeIfAbsent(sessionId, id -> new ConcurrentHashMap<>()).put(clusterId, credentials);
    }

    public void remove(String sessionId, String clusterId) {
        if (sessionId == null || clusterId == null) return;
        bySession.computeIfPresent(sessionId, (id, clusters) -> {
            clusters.remove(clusterId);
            return clusters.isEmpty() ? null : clusters;
        });
    }

}
