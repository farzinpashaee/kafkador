package com.csl.kafkador.service.ai;

/** Fully resolved provider settings (defaults applied). Holds the API key, so toString masks it. */
public record AiSettings(AiProviderType provider, String model, String baseUrl, String apiKey) {

    @Override
    public String toString() {
        return "AiSettings[provider=" + provider + ", model=" + model + ", baseUrl=" + baseUrl + ", apiKey=****]";
    }

}
