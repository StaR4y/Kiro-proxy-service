package xyz.star4y.kiroproxy.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import org.springframework.stereotype.Service;
import xyz.star4y.kiroproxy.proxy.catalog.ProxyModelCatalog;
import xyz.star4y.kiroproxy.proxy.catalog.ProxyModelSpec;

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
        long now = Instant.now().getEpochSecond();

        ProxyModelCatalog.models().forEach(spec -> addModel(data, spec, now));
        return root;
    }

    private void addModel(ArrayNode data, ProxyModelSpec spec, long created) {
        ObjectNode model = data.addObject();
        model.put("id", spec.id());
        model.put("object", "model");
        model.put("created", created);
        model.put("owned_by", spec.ownedBy());
        model.put("provider", spec.provider());
        model.put("family", spec.family());
        model.put("targetModelId", spec.targetModelId());
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
}
