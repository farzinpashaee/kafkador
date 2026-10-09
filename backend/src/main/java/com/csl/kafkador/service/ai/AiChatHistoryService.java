package com.csl.kafkador.service.ai;

import com.csl.kafkador.domain.dto.AiChatSessionDto;
import com.csl.kafkador.domain.dto.AiMessageDto;
import com.csl.kafkador.domain.model.AiChatMessage;
import com.csl.kafkador.domain.model.AiChatSession;
import com.csl.kafkador.exception.AiChatSessionNotFoundException;
import com.csl.kafkador.repository.AiChatMessageRepository;
import com.csl.kafkador.repository.AiChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Stores AI assistant conversations. A turn is only saved once the provider has answered it,
 * so a failed question leaves no half-finished exchange behind and can simply be sent again.
 */
@Service("AiChatHistoryService")
@RequiredArgsConstructor
public class AiChatHistoryService {

    static final int TITLE_LENGTH = 200;
    static final int MAX_SESSIONS = 200;

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;

    /** The most recently active sessions first, without their messages. */
    public List<AiChatSessionDto> getSessions() {
        return sessionRepository.findAllByOrderByUpdateDateTimeDesc(PageRequest.of(0, MAX_SESSIONS)).stream()
                .map(AiChatHistoryService::toDto)
                .toList();
    }

    public AiChatSessionDto getSession(String sessionId) throws AiChatSessionNotFoundException {
        AiChatSession session = findSession(sessionId);
        return toDto(session).setMessages(readMessages(session.getId()));
    }

    /** The stored turns of a conversation, oldest first; empty for a conversation that has not started yet. */
    public List<AiMessageDto> getMessages(String sessionId) throws AiChatSessionNotFoundException {
        if (sessionId == null || sessionId.isBlank()) return List.of();
        return readMessages(findSession(sessionId).getId());
    }

    /** Saves one answered turn, starting a new session when {@code sessionId} is blank. Returns the session id. */
    @Transactional
    public String saveTurn(String sessionId, String clusterId, String question, String reply) throws AiChatSessionNotFoundException {
        Date now = new Date();
        AiChatSession session;
        if (sessionId == null || sessionId.isBlank()) {
            session = new AiChatSession();
            session.setId(UUID.randomUUID().toString());
            session.setTitle(title(question));
            session.setClusterId(clusterId);
            session.setCreateDateTime(now);
        } else {
            session = findSession(sessionId);
        }
        session.setUpdateDateTime(now);
        sessionRepository.save(session);
        messageRepository.save(message(session.getId(), "user", question, now));
        messageRepository.save(message(session.getId(), "assistant", reply, now));
        return session.getId();
    }

    private AiChatSession findSession(String sessionId) throws AiChatSessionNotFoundException {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AiChatSessionNotFoundException("This chat no longer exists."));
    }

    private List<AiMessageDto> readMessages(String sessionId) {
        return messageRepository.findBySessionIdOrderByIdAsc(sessionId).stream()
                .map(m -> new AiMessageDto(m.getRole(), m.getContent()))
                .toList();
    }

    private static AiChatMessage message(String sessionId, String role, String content, Date time) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateDateTime(time);
        return message;
    }

    /** The first question, on one line and short enough for the history list. */
    static String title(String question) {
        String flat = question.strip().replaceAll("\\s+", " ");
        return flat.length() <= TITLE_LENGTH ? flat : flat.substring(0, TITLE_LENGTH - 3) + "...";
    }

    private static AiChatSessionDto toDto(AiChatSession session) {
        return new AiChatSessionDto()
                .setId(session.getId())
                .setTitle(session.getTitle())
                .setCreateDateTime(session.getCreateDateTime())
                .setUpdateDateTime(session.getUpdateDateTime());
    }

}
