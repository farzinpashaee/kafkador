package com.csl.kafkador.repository;


import com.csl.kafkador.model.Connection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionRepository extends JpaRepository<Connection, Integer> {
}
