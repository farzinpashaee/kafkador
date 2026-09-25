package com.csl.kafkador.service.ai;

import com.csl.kafkador.domain.dto.AiConfigDto;
import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.exception.ConfigurationRequiredException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stores the AI assistant configuration. The setting is global (not per cluster), so it is
 * kept under a fixed pseudo cluster id. The API key is only ever handed out through
 * {@link #getActiveSettings()} to the provider adapters, never through the DTO returned to clients.
 */
@Service("AiConfigService")
@RequiredArgsConstructor
public class AiConfigService {

    static final String GLOBAL_SCOPE = "global";
    private static final String KEY_PROVIDER = "kafkador.ai.provider";
    private static final String KEY_MODEL = "kafkador.ai.model";
    private static final String KEY_BASE_URL = "kafkador.ai.base-url";
    private static final String KEY_API_KEY = "kafkador.ai.api-key";
    private static final String KEY_ENABLED = "kafkador.ai.enabled";

    private final KafkadorConfigService<String, Map.Entry<String, String>> kafkadorConfigService;

    public AiConfigDto getConfig() {
        Map<String, KafkadorConfig> stored = kafkadorConfigService.get(GLOBAL_SCOPE);
        String provider = value(stored, KEY_PROVIDER);
        boolean apiKeySet = !value(stored, KEY_API_KEY).isBlank();
        boolean enabled = Boolean.parseBoolean(value(stored, KEY_ENABLED));
        return new AiConfigDto()
                .setProvider(provider.isBlank() ? AiProviderType.OPENAI.getKey() : provider)
                .setModel(value(stored, KEY_MODEL))
                .setBaseUrl(value(stored, KEY_BASE_URL))
                .setEnabled(enabled)
                .setApiKeySet(apiKeySet)
                .setAvailable(enabled && apiKeySet && AiProviderType.fromKey(provider).isPresent());
    }

    public AiConfigDto saveConfig(AiConfigDto config) throws ConfigurationRequiredException {
        Map<String, KafkadorConfig> stored = kafkadorConfigService.get(GLOBAL_SCOPE);
        boolean hasKey = !value(stored, KEY_API_KEY).isBlank() || (config.getApiKey() != null && !config.getApiKey().isBlank());
        if (config.isEnabled() && !hasKey) {
            throw new ConfigurationRequiredException("An API key is required to enable the AI assistant.");
        }
        put(KEY_PROVIDER, config.getProvider().toLowerCase());
        put(KEY_MODEL, blankToEmpty(config.getModel()));
        put(KEY_BASE_URL, blankToEmpty(config.getBaseUrl()));
        put(KEY_ENABLED, String.valueOf(config.isEnabled()));
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            put(KEY_API_KEY, config.getApiKey().trim());
        }
        return getConfig();
    }

    /** Resolved settings (defaults applied), only when the assistant is enabled and has a key. */
    public Optional<AiSettings> getActiveSettings() {
        Map<String, KafkadorConfig> stored = kafkadorConfigService.get(GLOBAL_SCOPE);
        if (!Boolean.parseBoolean(value(stored, KEY_ENABLED))) return Optional.empty();
        String apiKey = value(stored, KEY_API_KEY);
        if (apiKey.isBlank()) return Optional.empty();
        return AiProviderType.fromKey(value(stored, KEY_PROVIDER)).map(provider -> {
            String model = value(stored, KEY_MODEL);
            String baseUrl = value(stored, KEY_BASE_URL);
            return new AiSettings(provider,
                    model.isBlank() ? provider.getDefaultModel() : model,
                    baseUrl.isBlank() ? provider.getDefaultBaseUrl() : baseUrl,
                    apiKey);
        });
    }

    private void put(String key, String value) {
        kafkadorConfigService.save(new AbstractMap.SimpleEntry<>(key, value), GLOBAL_SCOPE);
    }

    private static String value(Map<String, KafkadorConfig> stored, String key) {
        KafkadorConfig entry = stored.get(key);
        return entry == null || entry.getConfigValue() == null ? "" : entry.getConfigValue();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

}
