package xyz.star4y.kiroproxy.proxy;

import java.util.Locale;
import xyz.star4y.kiroproxy.proxy.catalog.AnthropicModelCatalog;
import xyz.star4y.kiroproxy.proxy.catalog.ProxyModelCatalog;

public final class KiroModelMapper {

    private KiroModelMapper() {
    }

    public static String map(String model) {
        if (model == null || model.isBlank()) {
            return AnthropicModelCatalog.DEFAULT_MODEL_ID;
        }
        String trimmed = model.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String mapped = ProxyModelCatalog.targetFor(lower);
        if (mapped != null) {
            return mapped;
        }
        if (lower.startsWith("claude") || lower.startsWith("gpt") || lower.equals("simple-task")) {
            return lower.startsWith("gpt") ? AnthropicModelCatalog.DEFAULT_MODEL_ID : trimmed;
        }
        if (lower.matches("o\\d.*")) {
            return AnthropicModelCatalog.DEFAULT_MODEL_ID;
        }
        return trimmed;
    }
}
