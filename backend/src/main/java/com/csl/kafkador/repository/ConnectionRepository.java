package com.csl.kafkador.repository;

import com.csl.kafkador.dto.ConnectionDto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionRepository extends JpaRepository<ConnectionDto,Integer> {
}
