package com.csl.kafkador.service.ai;

import com.csl.kafkador.domain.dto.AiConfigDto;
import com.csl.kafkador.domain.model.KafkadorConfig;
import com.csl.kafkador.exception.ConfigNotFoundException;
import com.csl.kafkador.exception.ConfigurationRequiredException;
import com.csl.kafkador.service.config.KafkadorConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigServiceTest {

    private AiConfigService service;

    @BeforeEach
    void setUp() {
        service = new AiConfigService(new InMemoryConfigService());
    }

    @Test
    void defaults_areDisabledAndUnavailable() {
        AiConfigDto config = service.getConfig();

        assertThat(config.getProvider()).isEqualTo("openai");
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.isApiKeySet()).isFalse();
        assertThat(config.isAvailable()).isFalse();
        assertThat(service.getActiveSettings()).isEmpty();
    }

    @Test
    void save_neverExposesTheApiKey() throws Exception {
        AiConfigDto saved = service.saveConfig(request("anthropic", "claude-x", "", "secret-key", true));

        assertThat(saved.isApiKeySet()).isTrue();
        assertThat(saved.isAvailable()).isTrue();
        assertThat(new ObjectMapper().writeValueAsString(saved)).doesNotContain("secret-key").doesNotContain("\"apiKey\"");
        assertThat(saved.getApiKey()).isNull();
    }

    @Test
    void save_withBlankKeyKeepsTheStoredKey() throws Exception {
        service.saveConfig(request("openai", "", "", "first-key", true));
        service.saveConfig(request("gemini", "gemini-2.0-flash", "", "", true));

        AiSettings settings = service.getActiveSettings().orElseThrow();
        assertThat(settings.provider()).isEqualTo(AiProviderType.GEMINI);
        assertThat(settings.apiKey()).isEqualTo("first-key");
    }

    @Test
    void enablingWithoutAnyKey_isRejected() {
        assertThatThrownBy(() -> service.saveConfig(request("openai", "", "", "", true)))
                .isInstanceOf(ConfigurationRequiredException.class);
    }

    @Test
    void disabledConfig_hasNoActiveSettingsEvenWithAKey() throws Exception {
        service.saveConfig(request("openai", "", "", "key", false));

        assertThat(service.getConfig().isApiKeySet()).isTrue();
        assertThat(service.getConfig().isAvailable()).isFalse();
        assertThat(service.getActiveSettings()).isEmpty();
    }

    @Test
    void activeSettings_applyProviderDefaultsForBlankModelAndBaseUrl() throws Exception {
        service.saveConfig(request("anthropic", "", "", "key", true));

        AiSettings settings = service.getActiveSettings().orElseThrow();
        assertThat(settings.model()).isEqualTo(AiProviderType.ANTHROPIC.getDefaultModel());
        assertThat(settings.baseUrl()).isEqualTo(AiProviderType.ANTHROPIC.getDefaultBaseUrl());
        assertThat(settings.toString()).doesNotContain("key=key").contains("****");
    }

    @Test
    void activeSettings_useCustomModelAndBaseUrl() throws Exception {
        service.saveConfig(request("openai", "llama3", "http://localhost:11434/v1", "key", true));

        AiSettings settings = service.getActiveSettings().orElseThrow();
        assertThat(settings.model()).isEqualTo("llama3");
        assertThat(settings.baseUrl()).isEqualTo("http://localhost:11434/v1");
    }

    private static AiConfigDto request(String provider, String model, String baseUrl, String apiKey, boolean enabled) {
        return new AiConfigDto().setProvider(provider).setModel(model).setBaseUrl(baseUrl).setApiKey(apiKey).setEnabled(enabled);
    }

    private static class InMemoryConfigService implements KafkadorConfigService<String, Map.Entry<String, String>> {

        private final Map<String, KafkadorConfig> store = new HashMap<>();

        @Override
        public String get(String key, String clusterId) throws ConfigNotFoundException {
            KafkadorConfig config = store.get(clusterId + "|" + key);
            if (config == null) throw new ConfigNotFoundException("not found");
            return config.getConfigValue();
        }

        @Override
        public String save(Map.Entry<String, String> entry, String clusterId) {
            KafkadorConfig config = new KafkadorConfig();
            config.setClusterId(clusterId);
            config.setConfigKey(entry.getKey());
            config.setConfigValue(entry.getValue());
            store.put(clusterId + "|" + entry.getKey(), config);
            return entry.getValue();
        }

        @Override
        public Map<String, KafkadorConfig> get(String clusterId) {
            Map<String, KafkadorConfig> result = new HashMap<>();
            store.values().stream().filter(c -> clusterId.equals(c.getClusterId()))
                    .forEach(c -> result.put(c.getConfigKey(), c));
            return result;
        }
    }

}
