package com.csl.kafkador.repository;

import com.csl.kafkador.domain.model.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, Integer> {
}
