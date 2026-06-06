package xyz.star4y.kiroproxy.proxy.catalog;

import java.util.List;

public final class OpenAiModelCatalog {

    private static final List<ProxyModelSpec> MODELS = List.of(
        alias("gpt-5.2", "GPT-5.2", "GPT-5", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("gpt-5.2-pro", "GPT-5.2 pro", "GPT-5", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("gpt-5.1", "GPT-5.1", "GPT-5", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("gpt-5.1-chat-latest", "GPT-5.1 Chat", "GPT-5", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("gpt-5", "GPT-5", "GPT-5", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("gpt-5-mini", "GPT-5 mini", "GPT-5", AnthropicModelCatalog.HAIKU_MODEL_ID),
        alias("gpt-5-nano", "GPT-5 nano", "GPT-5", AnthropicModelCatalog.HAIKU_MODEL_ID),
        alias("gpt-4.1", "GPT-4.1", "GPT-4", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("gpt-4.1-mini", "GPT-4.1 mini", "GPT-4", AnthropicModelCatalog.HAIKU_MODEL_ID),
        alias("gpt-4.1-nano", "GPT-4.1 nano", "GPT-4", AnthropicModelCatalog.HAIKU_MODEL_ID),
        alias("gpt-4o", "GPT-4o", "GPT-4", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("gpt-4o-mini", "GPT-4o mini", "GPT-4", AnthropicModelCatalog.HAIKU_MODEL_ID),
        alias("chatgpt-4o", "ChatGPT-4o", "GPT-4", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("gpt-4", "GPT-4", "GPT-4", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("gpt-4-turbo", "GPT-4 Turbo", "GPT-4", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("gpt-3.5-turbo", "GPT-3.5 Turbo", "GPT-3.5", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("o3", "o3", "OpenAI Reasoning", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("o3-mini", "o3-mini", "OpenAI Reasoning", AnthropicModelCatalog.HAIKU_MODEL_ID),
        alias("o3-pro", "o3-pro", "OpenAI Reasoning", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("o4-mini", "o4-mini", "OpenAI Reasoning", AnthropicModelCatalog.HAIKU_MODEL_ID),
        alias("o1", "o1", "OpenAI Reasoning", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("o1-pro", "o1-pro", "OpenAI Reasoning", AnthropicModelCatalog.DEFAULT_MODEL_ID),
        alias("o1-mini", "o1-mini", "OpenAI Reasoning", AnthropicModelCatalog.HAIKU_MODEL_ID)
    );

    private OpenAiModelCatalog() {
    }

    public static List<ProxyModelSpec> models() {
        return MODELS;
    }

    private static ProxyModelSpec alias(String id, String name, String family, String targetModelId) {
        return new ProxyModelSpec(
            id,
            name + " compatible alias",
            "OpenAI",
            family,
            "kiro-proxy",
            "OpenAI-compatible alias for Kiro",
            AnthropicModelCatalog.TEXT_AND_IMAGE,
            AnthropicModelCatalog.DEFAULT_INPUT_TOKENS,
            AnthropicModelCatalog.DEFAULT_OUTPUT_TOKENS,
            targetModelId
        );
    }
}
