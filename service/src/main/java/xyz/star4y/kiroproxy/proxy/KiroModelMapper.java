package xyz.star4y.kiroproxy.proxy;

import java.util.Locale;
import java.util.Map;

public final class KiroModelMapper {

    private static final Map<String, String> MODEL_ID_MAP = Map.ofEntries(
        Map.entry("claude-sonnet-4-5", "claude-sonnet-4.5"),
        Map.entry("claude-sonnet-4.5", "claude-sonnet-4.5"),
        Map.entry("claude-haiku-4-5", "claude-haiku-4.5"),
        Map.entry("claude-haiku-4.5", "claude-haiku-4.5"),
        Map.entry("claude-opus-4-5", "claude-opus-4.5"),
        Map.entry("claude-opus-4.5", "claude-opus-4.5"),
        Map.entry("claude-sonnet-4", "claude-sonnet-4"),
        Map.entry("claude-sonnet-4-20250514", "claude-sonnet-4"),
        Map.entry("claude-3-5-sonnet", "claude-sonnet-4.5"),
        Map.entry("claude-3-opus", "claude-sonnet-4.5"),
        Map.entry("claude-3-sonnet", "claude-sonnet-4"),
        Map.entry("claude-3-haiku", "claude-haiku-4.5"),
        Map.entry("simple-task", "claude-haiku-4.5"),
        Map.entry("chatgpt-4o", "claude-sonnet-4.5"),
        Map.entry("gpt-5.2", "claude-sonnet-4.5"),
        Map.entry("gpt-5.2-pro", "claude-sonnet-4.5"),
        Map.entry("gpt-5.1", "claude-sonnet-4.5"),
        Map.entry("gpt-5.1-chat-latest", "claude-sonnet-4.5"),
        Map.entry("gpt-5", "claude-sonnet-4.5"),
        Map.entry("gpt-5-mini", "claude-haiku-4.5"),
        Map.entry("gpt-5-nano", "claude-haiku-4.5"),
        Map.entry("gpt-4.1", "claude-sonnet-4.5"),
        Map.entry("gpt-4.1-mini", "claude-haiku-4.5"),
        Map.entry("gpt-4.1-nano", "claude-haiku-4.5"),
        Map.entry("gpt-4", "claude-sonnet-4.5"),
        Map.entry("gpt-4o", "claude-sonnet-4.5"),
        Map.entry("gpt-4o-mini", "claude-haiku-4.5"),
        Map.entry("gpt-4-turbo", "claude-sonnet-4.5"),
        Map.entry("gpt-3.5-turbo", "claude-sonnet-4.5"),
        Map.entry("o3", "claude-sonnet-4.5"),
        Map.entry("o3-mini", "claude-haiku-4.5"),
        Map.entry("o3-pro", "claude-sonnet-4.5"),
        Map.entry("o4-mini", "claude-haiku-4.5"),
        Map.entry("o1", "claude-sonnet-4.5"),
        Map.entry("o1-pro", "claude-sonnet-4.5"),
        Map.entry("o1-mini", "claude-haiku-4.5")
    );

    private KiroModelMapper() {
    }

    public static String map(String model) {
        if (model == null || model.isBlank()) {
            return "claude-sonnet-4.5";
        }
        String trimmed = model.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String mapped = MODEL_ID_MAP.get(lower);
        if (mapped != null) {
            return mapped;
        }
        if (lower.startsWith("claude") || lower.startsWith("gpt") || lower.equals("simple-task")) {
            return lower.startsWith("gpt") ? "claude-sonnet-4.5" : trimmed;
        }
        if (lower.matches("o\\d.*")) {
            return "claude-sonnet-4.5";
        }
        return trimmed;
    }
}
