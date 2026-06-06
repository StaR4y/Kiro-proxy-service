package xyz.star4y.kiroproxy.proxy.catalog;

import java.util.List;

public final class AnthropicModelCatalog {

    public static final String DEFAULT_MODEL_ID = "claude-sonnet-4.5";
    public static final String HAIKU_MODEL_ID = "claude-haiku-4.5";

    static final int DEFAULT_INPUT_TOKENS = 200_000;
    static final int DEFAULT_OUTPUT_TOKENS = 64_000;
    static final List<String> TEXT_AND_IMAGE = List.of("TEXT", "IMAGE");
    static final List<String> TEXT_ONLY = List.of("TEXT");

    private static final List<ProxyModelSpec> MODELS = List.of(
        model("auto", "Auto", "Kiro Preset", "Auto select best model", "auto"),
        model(DEFAULT_MODEL_ID, "Claude Sonnet 4.5", "Claude 4", "The latest Claude Sonnet model", DEFAULT_MODEL_ID),
        model("claude-sonnet-4", "Claude Sonnet 4", "Claude 4", "Hybrid reasoning and coding", "claude-sonnet-4"),
        model(HAIKU_MODEL_ID, "Claude Haiku 4.5", "Claude 4", "The latest Claude Haiku model", HAIKU_MODEL_ID),
        model("claude-opus-4.5", "Claude Opus 4.5", "Claude 4", "The most powerful model", "claude-opus-4.5"),
        model("claude-3.7-sonnet", "Claude 3.7 Sonnet", "Claude 3", "Claude 3.7 Sonnet (hidden)", "claude-3.7-sonnet"),
        new ProxyModelSpec(
            "simple-task",
            "Simple Task",
            "Anthropic",
            "Kiro Preset",
            "kiro-api",
            "Kiro fast model for lightweight tasks",
            TEXT_ONLY,
            DEFAULT_INPUT_TOKENS,
            4_096,
            HAIKU_MODEL_ID
        ),
        model(
            "CLAUDE_SONNET_4_20250514_V1_0",
            "Claude Sonnet 4 (CW)",
            "CodeWhisperer Internal",
            "Claude Sonnet 4 (CodeWhisperer internal ID)",
            "CLAUDE_SONNET_4_20250514_V1_0"
        ),
        model(
            "CLAUDE_HAIKU_4_5_20251001_V1_0",
            "Claude Haiku 4.5 (CW)",
            "CodeWhisperer Internal",
            "Claude Haiku 4.5 (CodeWhisperer internal ID)",
            "CLAUDE_HAIKU_4_5_20251001_V1_0"
        ),
        model(
            "CLAUDE_3_7_SONNET_20250219_V1_0",
            "Claude 3.7 Sonnet (CW)",
            "CodeWhisperer Internal",
            "Claude 3.7 Sonnet (CodeWhisperer internal ID)",
            "CLAUDE_3_7_SONNET_20250219_V1_0"
        )
    );

    private AnthropicModelCatalog() {
    }

    public static List<ProxyModelSpec> models() {
        return MODELS;
    }

    private static ProxyModelSpec model(
        String id,
        String name,
        String family,
        String description,
        String targetModelId
    ) {
        return new ProxyModelSpec(
            id,
            name,
            "Anthropic",
            family,
            "kiro-api",
            description,
            TEXT_AND_IMAGE,
            DEFAULT_INPUT_TOKENS,
            DEFAULT_OUTPUT_TOKENS,
            targetModelId
        );
    }
}
