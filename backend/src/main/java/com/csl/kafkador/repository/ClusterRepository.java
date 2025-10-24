package com.csl.kafkador.repository;

import com.csl.kafkador.domain.model.Cluster;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClusterRepository extends JpaRepository<Cluster,String> {
}
