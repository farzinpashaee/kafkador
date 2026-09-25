package com.csl.kafkador.service.ai;

import java.util.Arrays;
import java.util.Optional;

public enum AiProviderType {

    OPENAI("openai", "OpenAI", "gpt-4o-mini", "https://api.openai.com/v1"),
    ANTHROPIC("anthropic", "Anthropic Claude", "claude-sonnet-5", "https://api.anthropic.com"),
    GEMINI("gemini", "Google Gemini", "gemini-2.0-flash", "https://generativelanguage.googleapis.com");

    private final String key;
    private final String displayName;
    private final String defaultModel;
    private final String defaultBaseUrl;

    AiProviderType(String key, String displayName, String defaultModel, String defaultBaseUrl) {
        this.key = key;
        this.displayName = displayName;
        this.defaultModel = defaultModel;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public String getDefaultModel() { return defaultModel; }
    public String getDefaultBaseUrl() { return defaultBaseUrl; }

    public static Optional<AiProviderType> fromKey(String key) {
        return Arrays.stream(values()).filter(p -> p.key.equalsIgnoreCase(key)).findFirst();
    }

}
