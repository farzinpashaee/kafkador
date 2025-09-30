package com.csl.kafkador.util;

import com.csl.kafkador.dto.ClusterDto;
import com.csl.kafkador.dto.ConnectionDto;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.LogDirDescription;
import org.apache.kafka.clients.admin.ReplicaInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class KafkaHelper {

    public static Map<Integer, Long> getReplicaSize(Map<Integer, Map<String, LogDirDescription>> map ){
        Map<Integer, Long> brokerBytes = new LinkedHashMap<>();
        for (var brokerEntry : map.entrySet()) {
            int brokerId = brokerEntry.getKey();
            long total = 0L;
            for (LogDirDescription ldd : brokerEntry.getValue().values()) {
                if (ldd.error() != null && !(ldd.error() instanceof org.apache.kafka.common.errors.ApiException) )
                    continue;
                for (ReplicaInfo ri : ldd.replicaInfos().values()) {
                    total += ri.size();
                }
            }
            brokerBytes.put(brokerId, total);
        }
        return brokerBytes;
    }


    public static Properties getConnectionProperties(String host, String port) {
        String bootstrapServers = host + ":" + port ;
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return properties;
    }

    public static Properties getConnectionProperties(ClusterDto cluster) {
        String bootstrapServers = cluster.getHost() + ":" + cluster.getPort() ;
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return properties;
    }

    public static Properties getConnectionProperties(ConnectionDto connection) {
        String bootstrapServers = connection.getHost() + ":" + connection.getPort() ;
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return properties;
    }

}
