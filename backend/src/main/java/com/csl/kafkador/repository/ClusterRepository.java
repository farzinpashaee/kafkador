package com.csl.kafkador.repository;

import com.csl.kafkador.domain.model.Cluster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClusterRepository extends JpaRepository<Cluster,String> {

    Optional<Cluster> findByHostAndPort(String host, String port);
    Optional<Cluster> findByClusterId(String clusterId);

}
