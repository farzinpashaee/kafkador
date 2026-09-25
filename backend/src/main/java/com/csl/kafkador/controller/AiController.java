package com.csl.kafkador.controller;

import com.csl.kafkador.domain.GenericResponse;
import com.csl.kafkador.domain.dto.AiChatRequestDto;
import com.csl.kafkador.domain.dto.AiChatResponseDto;
import com.csl.kafkador.domain.dto.AiConfigDto;
import com.csl.kafkador.domain.dto.ConnectionDto;
import com.csl.kafkador.exception.AiAssistantException;
import com.csl.kafkador.exception.ConfigurationRequiredException;
import com.csl.kafkador.service.ConnectionService;
import com.csl.kafkador.service.ai.AiAssistantService;
import com.csl.kafkador.service.ai.AiConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Validated
public class AiController {

    private final AiConfigService aiConfigService;
    private final AiAssistantService aiAssistantService;
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

    @PostMapping("/chat")
    public ResponseEntity<GenericResponse<AiChatResponseDto>> chat(@Valid @RequestBody AiChatRequestDto request)
            throws AiAssistantException, ConfigurationRequiredException {
        ConnectionDto connection = connectionService.getActiveConnection();
        String reply = aiAssistantService.chat(connection.getClusterId(), request.getMessages());
        return new GenericResponse.Builder<AiChatResponseDto>()
                .data(new AiChatResponseDto(reply))
                .success(HttpStatus.OK);
    }

}
