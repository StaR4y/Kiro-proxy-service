package xyz.star4y.kiroproxy.proxy.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class ProxyModelCatalog {

    private static final List<ProxyModelSpec> MODELS = buildModels();
    private static final Map<String, String> TARGET_BY_ID = MODELS.stream()
        .collect(Collectors.toUnmodifiableMap(spec -> key(spec.id()), ProxyModelSpec::targetModelId));

    private ProxyModelCatalog() {
    }

    public static List<ProxyModelSpec> models() {
        return MODELS;
    }

    public static String targetFor(String modelId) {
        if (modelId == null) {
            return null;
        }
        return TARGET_BY_ID.get(key(modelId));
    }

    private static List<ProxyModelSpec> buildModels() {
        List<ProxyModelSpec> models = new ArrayList<>();
        models.addAll(AnthropicModelCatalog.models());
        models.addAll(OpenAiModelCatalog.models());
        return List.copyOf(models);
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
