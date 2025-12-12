package com.csl.kafkador.repository;

import com.csl.kafkador.domain.model.Metric;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricRepository extends JpaRepository<Metric, Integer> {


}
