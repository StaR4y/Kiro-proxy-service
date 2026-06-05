package xyz.star4y.kiroproxy.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProxyModelService {

    private static final int DEFAULT_INPUT_TOKENS = 200_000;
    private static final int DEFAULT_OUTPUT_TOKENS = 64_000;
    private static final List<String> TEXT_AND_IMAGE = List.of("TEXT", "IMAGE");
    private static final List<String> TEXT_ONLY = List.of("TEXT");

    private final ObjectMapper objectMapper;
    private final long startedAt = Instant.now().toEpochMilli();

    public ProxyModelService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode health() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("status", "ok");
        root.put("service", "kiro-proxy-service");
        root.put("uptime_ms", Instant.now().toEpochMilli() - startedAt);
        return root;
    }

    public ObjectNode models() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("object", "list");
        ArrayNode data = root.putArray("data");
        long now = Instant.now().getEpochSecond();

        // Official Kiro models exposed by the current UI.
        addModel(data, model("auto", "Auto", now, "kiro-api", "Auto select best model"));
        addModel(data, model("claude-sonnet-4.5", "Claude Sonnet 4.5", now, "kiro-api", "The latest Claude Sonnet model"));
        addModel(data, model("claude-sonnet-4", "Claude Sonnet 4", now, "kiro-api", "Hybrid reasoning and coding"));
        addModel(data, model("claude-haiku-4.5", "Claude Haiku 4.5", now, "kiro-api", "The latest Claude Haiku model"));
        addModel(data, model("claude-opus-4.5", "Claude Opus 4.5", now, "kiro-api", "The most powerful model"));

        // Hidden models not returned by Kiro ListAvailableModels, but usable by compatible clients.
        addModel(data, model("claude-3.7-sonnet", "Claude 3.7 Sonnet", now, "kiro-api", "Claude 3.7 Sonnet (hidden)"));
        addModel(data, model("simple-task", "Simple Task", now, "kiro-api", "Kiro fast model for intent classification and lightweight tasks (routes to Haiku)", TEXT_ONLY, DEFAULT_INPUT_TOKENS, 4_096));
        addModel(data, model("CLAUDE_SONNET_4_20250514_V1_0", "Claude Sonnet 4 (CW)", now, "kiro-api", "Claude Sonnet 4 (CodeWhisperer internal ID)"));
        addModel(data, model("CLAUDE_HAIKU_4_5_20251001_V1_0", "Claude Haiku 4.5 (CW)", now, "kiro-api", "Claude Haiku 4.5 (CodeWhisperer internal ID)"));
        addModel(data, model("CLAUDE_3_7_SONNET_20250219_V1_0", "Claude 3.7 Sonnet (CW)", now, "kiro-api", "Claude 3.7 Sonnet (CodeWhisperer internal ID)"));

        // OpenAI-compatible aliases kept for SDKs and tools that validate model IDs client-side.
        addModel(data, openAiAlias("gpt-5.2", "GPT-5.2", now));
        addModel(data, openAiAlias("gpt-5.2-pro", "GPT-5.2 pro", now));
        addModel(data, openAiAlias("gpt-5.1", "GPT-5.1", now));
        addModel(data, openAiAlias("gpt-5.1-chat-latest", "GPT-5.1 Chat", now));
        addModel(data, openAiAlias("gpt-5", "GPT-5", now));
        addModel(data, openAiAlias("gpt-5-mini", "GPT-5 mini", now));
        addModel(data, openAiAlias("gpt-5-nano", "GPT-5 nano", now));
        addModel(data, openAiAlias("gpt-4.1", "GPT-4.1", now));
        addModel(data, openAiAlias("gpt-4.1-mini", "GPT-4.1 mini", now));
        addModel(data, openAiAlias("gpt-4.1-nano", "GPT-4.1 nano", now));
        addModel(data, openAiAlias("gpt-4o", "GPT-4o", now));
        addModel(data, openAiAlias("gpt-4o-mini", "GPT-4o mini", now));
        addModel(data, openAiAlias("chatgpt-4o", "ChatGPT-4o", now));
        addModel(data, openAiAlias("gpt-4", "GPT-4", now));
        addModel(data, openAiAlias("gpt-4-turbo", "GPT-4 Turbo", now));
        addModel(data, openAiAlias("gpt-3.5-turbo", "GPT-3.5 Turbo", now));
        addModel(data, openAiAlias("o3", "o3", now));
        addModel(data, openAiAlias("o3-mini", "o3-mini", now));
        addModel(data, openAiAlias("o3-pro", "o3-pro", now));
        addModel(data, openAiAlias("o4-mini", "o4-mini", now));
        addModel(data, openAiAlias("o1", "o1", now));
        addModel(data, openAiAlias("o1-pro", "o1-pro", now));
        addModel(data, openAiAlias("o1-mini", "o1-mini", now));
        return root;
    }

    private ModelSpec model(String id, String name, long created, String ownedBy, String description) {
        return model(id, name, created, ownedBy, description, TEXT_AND_IMAGE, DEFAULT_INPUT_TOKENS, DEFAULT_OUTPUT_TOKENS);
    }

    private ModelSpec openAiAlias(String id, String name, long created) {
        return model(id, name + " compatible alias", created, "kiro-proxy", "OpenAI-compatible alias for Kiro");
    }

    private ModelSpec model(
        String id,
        String name,
        long created,
        String ownedBy,
        String description,
        List<String> supportedInputTypes,
        int maxInputTokens,
        int maxOutputTokens
    ) {
        return new ModelSpec(id, name, created, ownedBy, description, supportedInputTypes, maxInputTokens, maxOutputTokens);
    }

    private void addModel(ArrayNode data, ModelSpec spec) {
        ObjectNode model = data.addObject();
        model.put("id", spec.id());
        model.put("object", "model");
        model.put("created", spec.created());
        model.put("owned_by", spec.ownedBy());
        model.put("name", spec.name());
        model.put("modelName", spec.name());
        model.put("description", spec.description());
        ArrayNode inputTypes = model.putArray("supportedInputTypes");
        spec.supportedInputTypes().forEach(inputTypes::add);
        model.put("maxInputTokens", spec.maxInputTokens());
        model.put("maxOutputTokens", spec.maxOutputTokens());
        model.put("context_length", spec.maxInputTokens());
        model.put("max_output_tokens", spec.maxOutputTokens());
    }

    private record ModelSpec(
        String id,
        String name,
        long created,
        String ownedBy,
        String description,
        List<String> supportedInputTypes,
        int maxInputTokens,
        int maxOutputTokens
    ) {
    }
}
