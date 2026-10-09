package com.csl.kafkador.repository;

import com.csl.kafkador.domain.model.AiChatSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, String> {

    List<AiChatSession> findAllByOrderByUpdateDateTimeDesc(Pageable pageable);

}
