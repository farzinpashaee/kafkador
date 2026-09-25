package com.csl.kafkador.service.ai;

import com.csl.kafkador.domain.dto.AiMessageDto;
import com.csl.kafkador.exception.AiAssistantException;
import com.csl.kafkador.exception.ConfigurationRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAssistantServiceTest {

    private AiConfigService configService;
    private AiContextBuilder contextBuilder;
    private AiProvider openAi;
    private AiAssistantService service;

    @BeforeEach
    void setUp() {
        configService = mock(AiConfigService.class);
        contextBuilder = mock(AiContextBuilder.class);
        openAi = mock(AiProvider.class);
        when(openAi.type()).thenReturn(AiProviderType.OPENAI);
        service = new AiAssistantService(configService, contextBuilder, List.of(openAi));
    }

    @Test
    void chat_whenNotEnabled_requiresConfiguration() {
        when(configService.getActiveSettings()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.chat("c1", List.of(new AiMessageDto("user", "hi"))))
                .isInstanceOf(ConfigurationRequiredException.class);
        verify(contextBuilder, never()).build(any(), any());
    }

    @Test
    void chat_sendsReadOnlySystemPromptWithSnapshotToTheConfiguredProvider() throws Exception {
        AiSettings settings = new AiSettings(AiProviderType.OPENAI, "gpt", "https://x", "key");
        when(configService.getActiveSettings()).thenReturn(Optional.of(settings));
        when(contextBuilder.build(eq("c1"), eq("Which topics?"))).thenReturn("SNAPSHOT-TEXT");
        when(openAi.chat(eq(settings), any(), any())).thenReturn("reply");

        String reply = service.chat("c1", List.of(new AiMessageDto("user", "Which topics?")));

        assertThat(reply).isEqualTo("reply");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(openAi).chat(eq(settings), prompt.capture(), any());
        assertThat(prompt.getValue()).contains("READ-ONLY").contains("You cannot change anything").endsWith("SNAPSHOT-TEXT");
    }

    @Test
    void chat_unsupportedProvider_isReported() {
        AiSettings settings = new AiSettings(AiProviderType.GEMINI, "m", "https://x", "key");
        when(configService.getActiveSettings()).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> service.chat("c1", List.of(new AiMessageDto("user", "hi"))))
                .isInstanceOf(AiAssistantException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void normalize_dropsLeadingAssistantAndTrailingAssistantTurns() {
        List<AiMessageDto> result = AiAssistantService.normalize(List.of(
                new AiMessageDto("assistant", "welcome"),
                new AiMessageDto("user", "q1"),
                new AiMessageDto("assistant", "a1"),
                new AiMessageDto("user", "q2"),
                new AiMessageDto("assistant", "dangling")));

        assertThat(result).extracting(AiMessageDto::getContent).containsExactly("q1", "a1", "q2");
    }

}
