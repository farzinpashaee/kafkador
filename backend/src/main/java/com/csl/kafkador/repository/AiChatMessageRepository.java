package com.csl.kafkador.repository;

import com.csl.kafkador.domain.model.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    List<AiChatMessage> findBySessionIdOrderByIdAsc(String sessionId);

}
