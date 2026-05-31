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

        // GPT-compatible aliases kept for OpenAI clients.
        addModel(data, model("gpt-4o", "GPT-4o compatible alias", now, "kiro-proxy", "GPT-compatible alias for Kiro"));
        addModel(data, model("gpt-4", "GPT-4 compatible alias", now, "kiro-proxy", "GPT-compatible alias for Kiro"));
        addModel(data, model("gpt-4-turbo", "GPT-4 Turbo compatible alias", now, "kiro-proxy", "GPT-compatible alias for Kiro"));
        addModel(data, model("gpt-3.5-turbo", "GPT-3.5 Turbo compatible alias", now, "kiro-proxy", "GPT-compatible alias for Kiro"));
        return root;
    }

    private ModelSpec model(String id, String name, long created, String ownedBy, String description) {
        return model(id, name, created, ownedBy, description, TEXT_AND_IMAGE, DEFAULT_INPUT_TOKENS, DEFAULT_OUTPUT_TOKENS);
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
