package xyz.star4y.kiroproxy.proxy.catalog;

import java.util.List;

public record ProxyModelSpec(
    String id,
    String name,
    String provider,
    String family,
    String ownedBy,
    String description,
    List<String> supportedInputTypes,
    int maxInputTokens,
    int maxOutputTokens,
    String targetModelId
) {
}
