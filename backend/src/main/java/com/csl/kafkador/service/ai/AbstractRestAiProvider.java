package com.csl.kafkador.service.ai;

import com.csl.kafkador.exception.AiAssistantException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

@Slf4j
abstract class AbstractRestAiProvider implements AiProvider {

    private static final int MAX_PROVIDER_MESSAGE_LENGTH = 300;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;

    protected AbstractRestAiProvider(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(90))
                .build();
    }

    protected JsonNode post(URI uri, HttpHeaders headers, Map<String, Object> body, String apiKey) throws AiAssistantException {
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            JsonNode response = restTemplate.postForObject(uri, new HttpEntity<>(body, headers), JsonNode.class);
            if (response == null) throw new AiAssistantException("The AI provider returned an empty response.");
            return response;
        } catch (HttpStatusCodeException e) {
            throw translate(e, apiKey);
        } catch (RestClientException e) {
            log.warn("AI provider {} unreachable: {}", type(), redact(e.getMessage(), apiKey));
            throw new AiAssistantException("Could not reach the AI provider. Check the base URL and network access.");
        }
    }

    private AiAssistantException translate(HttpStatusCodeException e, String apiKey) {
        int status = e.getStatusCode().value();
        log.warn("AI provider {} returned HTTP {}", type(), status);
        if (status == 401 || status == 403) {
            return new AiAssistantException("The AI provider rejected the API key or does not allow this request.");
        }
        if (status == 404) {
            return new AiAssistantException("The AI provider could not find the model or endpoint. Check the model name and base URL.");
        }
        if (status == 429) {
            return new AiAssistantException("The AI provider rate limit or quota was exceeded. Try again later.");
        }
        String detail = extractErrorMessage(e.getResponseBodyAsString());
        if (status < 500 && detail != null) {
            return new AiAssistantException("The AI provider rejected the request: " + redact(detail, apiKey));
        }
        return new AiAssistantException("The AI provider could not complete this request (HTTP " + status + ").");
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode error = MAPPER.readTree(body).path("error");
            String message = error.isTextual() ? error.asText() : error.path("message").asText("");
            if (message.isBlank()) return null;
            return message.length() > MAX_PROVIDER_MESSAGE_LENGTH ? message.substring(0, MAX_PROVIDER_MESSAGE_LENGTH) + "..." : message;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String redact(String text, String apiKey) {
        if (text == null || apiKey == null || apiKey.isBlank()) return text;
        return text.replace(apiKey, "****");
    }

    protected static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    protected static String requireReply(String reply) throws AiAssistantException {
        if (reply == null || reply.isBlank()) {
            throw new AiAssistantException("The AI provider returned no answer (it may have been blocked by a safety filter).");
        }
        return reply.trim();
    }

}
