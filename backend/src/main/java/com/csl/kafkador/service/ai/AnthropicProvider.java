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

@Component
public class AnthropicProvider extends AbstractRestAiProvider {

    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 2048;

    public AnthropicProvider(RestTemplateBuilder builder) {
        super(builder);
    }

    @Override
    public AiProviderType type() {
        return AiProviderType.ANTHROPIC;
    }

    @Override
    public String chat(AiSettings settings, String systemPrompt, List<AiMessageDto> messages) throws AiAssistantException {
        List<Map<String, String>> payload = new ArrayList<>();
        messages.forEach(m -> payload.add(Map.of("role", m.getRole(), "content", m.getContent())));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model());
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", systemPrompt);
        body.put("messages", payload);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", settings.apiKey());
        headers.set("anthropic-version", API_VERSION);

        JsonNode response = post(URI.create(trimTrailingSlash(settings.baseUrl()) + "/v1/messages"), headers, body, settings.apiKey());
        StringBuilder reply = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) reply.append(block.path("text").asText(""));
        }
        return requireReply(reply.toString());
    }

}
