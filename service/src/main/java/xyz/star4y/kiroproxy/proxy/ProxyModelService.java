package xyz.star4y.kiroproxy.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ProxyModelService {

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
        addModel(data, "claude-sonnet-4.5", "Claude Sonnet 4.5", 200_000, 32_000);
        addModel(data, "claude-sonnet-4", "Claude Sonnet 4", 200_000, 32_000);
        addModel(data, "claude-haiku-4.5", "Claude Haiku 4.5", 200_000, 8_192);
        addModel(data, "gpt-4o", "GPT-4o compatible alias", 200_000, 32_000);
        return root;
    }

    private void addModel(ArrayNode data, String id, String name, int context, int output) {
        ObjectNode model = data.addObject();
        model.put("id", id);
        model.put("object", "model");
        model.put("created", 0);
        model.put("owned_by", "kiro");
        model.put("name", name);
        model.put("context_length", context);
        model.put("max_output_tokens", output);
    }
}
