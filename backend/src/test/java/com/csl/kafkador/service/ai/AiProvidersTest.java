package com.csl.kafkador.service.ai;

import com.csl.kafkador.domain.dto.AiMessageDto;
import com.csl.kafkador.exception.AiAssistantException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiProvidersTest {

    private static final List<AiMessageDto> CONVERSATION = List.of(
            new AiMessageDto("user", "How many topics?"),
            new AiMessageDto("assistant", "Three."),
            new AiMessageDto("user", "Which ones?"));

    private static <P extends AiProvider> Fixture<P> fixture(Function<RestTemplateBuilder, P> factory) {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        P provider = factory.apply(new RestTemplateBuilder().additionalCustomizers(customizer));
        return new Fixture<>(provider, customizer.getServer());
    }

    private record Fixture<P>(P provider, MockRestServiceServer server) {}

    @Test
    void openAi_sendsBearerKeySystemPromptAndHistory_andParsesReply() throws Exception {
        Fixture<OpenAiProvider> f = fixture(OpenAiProvider::new);
        f.server().expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("SYSTEM"))
                .andExpect(jsonPath("$.messages[1].content").value("How many topics?"))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[3].content").value("Which ones?"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"orders, payments\"}}]}", MediaType.APPLICATION_JSON));

        String reply = f.provider().chat(settings(AiProviderType.OPENAI, "sk-test"), "SYSTEM", CONVERSATION);

        assertThat(reply).isEqualTo("orders, payments");
        f.server().verify();
    }

    @Test
    void openAi_honoursCustomBaseUrlWithTrailingSlash() throws Exception {
        Fixture<OpenAiProvider> f = fixture(OpenAiProvider::new);
        f.server().expect(requestTo("http://localhost:11434/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", MediaType.APPLICATION_JSON));

        AiSettings custom = new AiSettings(AiProviderType.OPENAI, "llama3", "http://localhost:11434/v1/", "key");
        assertThat(f.provider().chat(custom, "SYSTEM", CONVERSATION)).isEqualTo("ok");
    }

    @Test
    void anthropic_sendsKeyHeaderVersionSystemAndMessages_andJoinsTextBlocks() throws Exception {
        Fixture<AnthropicProvider> f = fixture(AnthropicProvider::new);
        f.server().expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(header("x-api-key", "ant-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.system").value("SYSTEM"))
                .andExpect(jsonPath("$.max_tokens").value(2048))
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andRespond(withSuccess("{\"content\":[{\"type\":\"text\",\"text\":\"Hello \"},{\"type\":\"text\",\"text\":\"there\"}]}", MediaType.APPLICATION_JSON));

        String reply = f.provider().chat(settings(AiProviderType.ANTHROPIC, "ant-key"), "SYSTEM", CONVERSATION);

        assertThat(reply).isEqualTo("Hello there");
        f.server().verify();
    }

    @Test
    void gemini_usesHeaderKeyNotQueryString_mapsAssistantToModelRole() throws Exception {
        Fixture<GeminiProvider> f = fixture(GeminiProvider::new);
        f.server().expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"))
                .andExpect(header("x-goog-api-key", "g-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("SYSTEM"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[1].role").value("model"))
                .andExpect(jsonPath("$.contents[1].parts[0].text").value("Three."))
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Answer\"}]}}]}", MediaType.APPLICATION_JSON));

        String reply = f.provider().chat(settings(AiProviderType.GEMINI, "g-key"), "SYSTEM", CONVERSATION);

        assertThat(reply).isEqualTo("Answer");
        f.server().verify();
    }

    @Test
    void unauthorized_isTranslatedToSafeMessage() {
        Fixture<OpenAiProvider> f = fixture(OpenAiProvider::new);
        f.server().expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Incorrect API key provided: sk-secret-key\"}}"));

        assertThatThrownBy(() -> f.provider().chat(settings(AiProviderType.OPENAI, "sk-secret-key"), "SYSTEM", CONVERSATION))
                .isInstanceOf(AiAssistantException.class)
                .hasMessageContaining("rejected the API key")
                .hasMessageNotContaining("sk-secret-key");
    }

    @Test
    void clientError_includesProviderMessageButRedactsKey() {
        Fixture<OpenAiProvider> f = fixture(OpenAiProvider::new);
        f.server().expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Context too long for key sk-secret-key\"}}"));

        assertThatThrownBy(() -> f.provider().chat(settings(AiProviderType.OPENAI, "sk-secret-key"), "SYSTEM", CONVERSATION))
                .isInstanceOf(AiAssistantException.class)
                .hasMessageContaining("Context too long")
                .hasMessageNotContaining("sk-secret-key");
    }

    @Test
    void emptyReply_isReportedAsError() {
        Fixture<GeminiProvider> f = fixture(GeminiProvider::new);
        f.server().expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.provider().chat(settings(AiProviderType.GEMINI, "g-key"), "SYSTEM", CONVERSATION))
                .isInstanceOf(AiAssistantException.class)
                .hasMessageContaining("no answer");
    }

    private static AiSettings settings(AiProviderType type, String key) {
        return new AiSettings(type, type.getDefaultModel(), type.getDefaultBaseUrl(), key);
    }

}
