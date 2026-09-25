package com.csl.kafkador.service.ai;

import com.csl.kafkador.domain.dto.AiMessageDto;
import com.csl.kafkador.exception.AiAssistantException;

import java.util.List;

/** Adapter for one LLM vendor. Add a vendor by implementing this and registering a Spring bean. */
public interface AiProvider {

    AiProviderType type();

    String chat(AiSettings settings, String systemPrompt, List<AiMessageDto> messages) throws AiAssistantException;

}
