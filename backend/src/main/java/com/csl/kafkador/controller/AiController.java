package com.csl.kafkador.controller;

import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.domain.dto.AiChatRequestDto;
import com.csl.kafkador.domain.dto.AiChatResponseDto;
import com.csl.kafkador.domain.dto.AiChatSessionDto;
import com.csl.kafkador.domain.dto.AiConfigDto;
import com.csl.kafkador.domain.dto.AiMessageDto;
import com.csl.kafkador.domain.dto.ConnectionDto;
import com.csl.kafkador.exception.AiAssistantException;
import com.csl.kafkador.exception.AiChatSessionNotFoundException;
import com.csl.kafkador.exception.ConfigurationRequiredException;
import com.csl.kafkador.service.ConnectionService;
import com.csl.kafkador.service.ai.AiAssistantService;
import com.csl.kafkador.service.ai.AiChatHistoryService;
import com.csl.kafkador.service.ai.AiConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Validated
public class AiController {

    /** How many messages of a session, including the new question, are sent to the provider. */
    static final int MAX_HISTORY = 20;

    private final AiConfigService aiConfigService;
    private final AiAssistantService aiAssistantService;
    private final AiChatHistoryService aiChatHistoryService;
    private final ConnectionService connectionService;

    @GetMapping("/config")
    public ResponseEntity<GenericResponse<AiConfigDto>> getConfig() {
        return new GenericResponse.Builder<AiConfigDto>()
                .data(aiConfigService.getConfig())
                .success(HttpStatus.OK);
    }

    @PutMapping("/config")
    public ResponseEntity<GenericResponse<AiConfigDto>> saveConfig(@Valid @RequestBody AiConfigDto config)
            throws ConfigurationRequiredException {
        return new GenericResponse.Builder<AiConfigDto>()
                .data(aiConfigService.saveConfig(config))
                .success(HttpStatus.OK);
    }

    /** Answers one question; the earlier turns of the session come from the database, not the client. */
    @PostMapping("/chat")
    public ResponseEntity<GenericResponse<AiChatResponseDto>> chat(@Valid @RequestBody AiChatRequestDto request)
            throws AiAssistantException, ConfigurationRequiredException, AiChatSessionNotFoundException {
        ConnectionDto connection = connectionService.getActiveConnection();
        String question = request.getMessage().trim();
        List<AiMessageDto> history = aiChatHistoryService.getMessages(request.getSessionId());
        List<AiMessageDto> conversation = new ArrayList<>(history.subList(Math.max(0, history.size() - (MAX_HISTORY - 1)), history.size()));
        conversation.add(new AiMessageDto("user", question));

        String reply = aiAssistantService.chat(connection.getClusterId(), conversation);
        String sessionId = aiChatHistoryService.saveTurn(request.getSessionId(), connection.getClusterId(), question, reply);
        return new GenericResponse.Builder<AiChatResponseDto>()
                .data(new AiChatResponseDto(sessionId, reply))
                .success(HttpStatus.OK);
    }

    @GetMapping("/sessions")
    public ResponseEntity<GenericResponse<List<AiChatSessionDto>>> getSessions() {
        return new GenericResponse.Builder<List<AiChatSessionDto>>()
                .data(aiChatHistoryService.getSessions())
                .success(HttpStatus.OK);
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<GenericResponse<AiChatSessionDto>> getSession(@PathVariable String id)
            throws AiChatSessionNotFoundException {
        return new GenericResponse.Builder<AiChatSessionDto>()
                .data(aiChatHistoryService.getSession(id))
                .success(HttpStatus.OK);
    }

}
