package com.csl.kafkador.service.ai;

import com.csl.kafkador.domain.dto.AiMessageDto;
import com.csl.kafkador.exception.AiAssistantException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeminiProvider extends AbstractRestAiProvider {

    public GeminiProvider(RestTemplateBuilder builder) {
        super(builder);
    }

    @Override
    public AiProviderType type() {
        return AiProviderType.GEMINI;
    }

    @Override
    public String chat(AiSettings settings, String systemPrompt, List<AiMessageDto> messages) throws AiAssistantException {
        List<Map<String, Object>> contents = new ArrayList<>();
        messages.forEach(m -> contents.add(Map.of(
                "role", "assistant".equals(m.getRole()) ? "model" : "user",
                "parts", List.of(Map.of("text", m.getContent())))));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        body.put("contents", contents);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-goog-api-key", settings.apiKey());

        URI uri = UriComponentsBuilder.fromUriString(trimTrailingSlash(settings.baseUrl()))
                .pathSegment("v1beta", "models", settings.model() + ":generateContent")
                .build().encode().toUri();

        JsonNode response = post(uri, headers, body, settings.apiKey());
        StringBuilder reply = new StringBuilder();
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            reply.append(part.path("text").asText(""));
        }
        return requireReply(reply.toString());
    }

}
