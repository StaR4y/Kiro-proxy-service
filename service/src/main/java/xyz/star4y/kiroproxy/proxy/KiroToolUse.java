package xyz.star4y.kiroproxy.proxy;

import com.fasterxml.jackson.databind.JsonNode;

public record KiroToolUse(
    String id,
    String name,
    JsonNode input
) {
}
