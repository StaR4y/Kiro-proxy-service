package xyz.star4y.kiroproxy.proxy;

import java.util.List;

public record KiroCompletionResult(
    String content,
    List<KiroToolUse> toolUses,
    KiroUsage usage
) {
    public KiroCompletionResult(String content, KiroUsage usage) {
        this(content, List.of(), usage);
    }

    public KiroCompletionResult {
        content = content == null ? "" : content;
        toolUses = toolUses == null ? List.of() : List.copyOf(toolUses);
    }
}