package com.csl.kafkador.repository;

import com.csl.kafkador.domain.model.KafkadorConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KafkadorConfigRepository extends JpaRepository<KafkadorConfig,Integer> {

    Optional<KafkadorConfig> findByConfigKey(String configKey);

}
