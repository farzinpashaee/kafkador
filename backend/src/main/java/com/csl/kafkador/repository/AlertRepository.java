package com.csl.kafkador.repository;

import com.csl.kafkador.domain.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Integer> {
}
