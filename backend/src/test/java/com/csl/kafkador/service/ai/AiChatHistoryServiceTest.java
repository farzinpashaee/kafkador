package com.csl.kafkador.service.ai;

import com.csl.kafkador.domain.dto.AiChatSessionDto;
import com.csl.kafkador.domain.dto.AiMessageDto;
import com.csl.kafkador.domain.model.AiChatMessage;
import com.csl.kafkador.domain.model.AiChatSession;
import com.csl.kafkador.exception.AiChatSessionNotFoundException;
import com.csl.kafkador.repository.AiChatMessageRepository;
import com.csl.kafkador.repository.AiChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatHistoryServiceTest {

    private AiChatSessionRepository sessionRepository;
    private AiChatMessageRepository messageRepository;
    private AiChatHistoryService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(AiChatSessionRepository.class);
        messageRepository = mock(AiChatMessageRepository.class);
        service = new AiChatHistoryService(sessionRepository, messageRepository);
    }

    @Test
    void saveTurn_withoutSession_startsOneTitledWithTheFirstQuestion() throws Exception {
        String id = service.saveTurn(null, "c1", "Which   topics\nexist?", "Three.");

        ArgumentCaptor<AiChatSession> session = ArgumentCaptor.forClass(AiChatSession.class);
        verify(sessionRepository).save(session.capture());
        assertThat(session.getValue().getId()).isEqualTo(id).isNotBlank();
        assertThat(session.getValue().getTitle()).isEqualTo("Which topics exist?");
        assertThat(session.getValue().getClusterId()).isEqualTo("c1");

        ArgumentCaptor<AiChatMessage> messages = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageRepository, times(2)).save(messages.capture());
        assertThat(messages.getAllValues()).extracting(AiChatMessage::getRole).containsExactly("user", "assistant");
        assertThat(messages.getAllValues()).extracting(AiChatMessage::getSessionId).containsOnly(id);
    }

    @Test
    void saveTurn_withExistingSession_keepsItsTitleAndAppends() throws Exception {
        AiChatSession existing = session("s1", "First question");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(existing));

        String id = service.saveTurn("s1", "c1", "Follow-up", "Answer");

        assertThat(id).isEqualTo("s1");
        assertThat(existing.getTitle()).isEqualTo("First question");
        verify(messageRepository, times(2)).save(any());
    }

    @Test
    void saveTurn_withUnknownSession_fails() {
        when(sessionRepository.findById("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveTurn("gone", "c1", "q", "a"))
                .isInstanceOf(AiChatSessionNotFoundException.class);
    }

    @Test
    void getMessages_withoutSession_isEmpty() throws Exception {
        assertThat(service.getMessages(null)).isEmpty();
    }

    @Test
    void getSession_returnsTheMessagesInOrder() throws Exception {
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session("s1", "Hi")));
        when(messageRepository.findBySessionIdOrderByIdAsc("s1")).thenReturn(List.of(message("user", "Hi"), message("assistant", "Hello")));

        AiChatSessionDto dto = service.getSession("s1");

        assertThat(dto.getTitle()).isEqualTo("Hi");
        assertThat(dto.getMessages()).containsExactly(new AiMessageDto("user", "Hi"), new AiMessageDto("assistant", "Hello"));
    }

    @Test
    void title_isShortenedForLongQuestions() {
        String title = AiChatHistoryService.title("x".repeat(500));

        assertThat(title).hasSize(AiChatHistoryService.TITLE_LENGTH).endsWith("...");
    }

    private static AiChatSession session(String id, String title) {
        AiChatSession session = new AiChatSession();
        session.setId(id);
        session.setTitle(title);
        session.setCreateDateTime(new Date());
        return session;
    }

    private static AiChatMessage message(String role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

}
