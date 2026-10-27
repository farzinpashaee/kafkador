package com.csl.kafkador.repository;

import com.csl.kafkador.domain.model.KafkadorUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KafkadorUserRepository extends JpaRepository<KafkadorUser, Integer> {

    Optional<KafkadorUser> findByUsernameIgnoreCase(String username);

}
