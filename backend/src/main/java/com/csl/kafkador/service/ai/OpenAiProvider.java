package com.csl.kafkador.service.ai;

import com.csl.kafkador.domain.dto.AiMessageDto;
import com.csl.kafkador.exception.AiAssistantException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAI Chat Completions; also works with OpenAI-compatible servers through a custom base URL. */
@Component
public class OpenAiProvider extends AbstractRestAiProvider {

    public OpenAiProvider(RestTemplateBuilder builder) {
        super(builder);
    }

    @Override
    public AiProviderType type() {
        return AiProviderType.OPENAI;
    }

    @Override
    public String chat(AiSettings settings, String systemPrompt, List<AiMessageDto> messages) throws AiAssistantException {
        List<Map<String, String>> payload = new ArrayList<>();
        payload.add(Map.of("role", "system", "content", systemPrompt));
        messages.forEach(m -> payload.add(Map.of("role", m.getRole(), "content", m.getContent())));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model());
        body.put("messages", payload);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(settings.apiKey());

        JsonNode response = post(URI.create(trimTrailingSlash(settings.baseUrl()) + "/chat/completions"), headers, body, settings.apiKey());
        return requireReply(response.path("choices").path(0).path("message").path("content").asText(null));
    }

}
