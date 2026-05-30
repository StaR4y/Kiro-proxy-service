package xyz.star4y.kiroproxy.proxy;

import java.math.BigDecimal;

public record KiroUsage(
    long inputTokens,
    long outputTokens,
    BigDecimal credits,
    long cacheReadTokens,
    long cacheWriteTokens,
    long reasoningTokens
) {

    public static KiroUsage zero() {
        return new KiroUsage(0, 0, BigDecimal.ZERO, 0, 0, 0);
    }

    public long totalTokens() {
        return inputTokens + outputTokens;
    }
}
