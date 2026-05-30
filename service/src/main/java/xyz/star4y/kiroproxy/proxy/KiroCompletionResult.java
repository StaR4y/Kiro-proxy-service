package xyz.star4y.kiroproxy.proxy;

public record KiroCompletionResult(
    String content,
    KiroUsage usage
) {
}
