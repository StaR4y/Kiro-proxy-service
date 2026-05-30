package xyz.star4y.kiroproxy.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class AwsEventStreamParser {

    private final ObjectMapper objectMapper;

    public AwsEventStreamParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public KiroCompletionResult parse(byte[] bytes, String fallbackModel, byte[] requestPayload) {
        StringBuilder content = new StringBuilder();
        UsageAccumulator usage = new UsageAccumulator(estimateTokens(requestPayload), 0, BigDecimal.ZERO, 0, 0, 0);
        int offset = 0;
        while (offset + 16 <= bytes.length) {
            int totalLength = readInt(bytes, offset);
            int headersLength = readInt(bytes, offset + 4);
            if (totalLength <= 16 || offset + totalLength > bytes.length) {
                break;
            }
            int payloadStart = offset + 12 + headersLength;
            int payloadEnd = offset + totalLength - 4;
            if (payloadStart >= 12 && payloadStart < payloadEnd && payloadEnd <= bytes.length) {
                parsePayload(bytes, payloadStart, payloadEnd, content, usage, fallbackModel);
            }
            offset += totalLength;
        }
        if (content.isEmpty()) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            JsonNode maybeJson = readJsonOrNull(text);
            if (maybeJson != null) {
                extractContent(maybeJson, content);
                extractUsage(maybeJson, usage, fallbackModel);
            }
        }
        if (usage.outputTokens == 0 && !content.isEmpty()) {
            usage.outputTokens = estimateTokens(content.toString().getBytes(StandardCharsets.UTF_8));
        }
        return new KiroCompletionResult(content.toString(), usage.toUsage());
    }

    private void parsePayload(
        byte[] bytes,
        int payloadStart,
        int payloadEnd,
        StringBuilder content,
        UsageAccumulator usage,
        String fallbackModel
    ) {
        String payloadText = new String(bytes, payloadStart, payloadEnd - payloadStart, StandardCharsets.UTF_8);
        JsonNode json = readJsonOrNull(payloadText);
        if (json == null) {
            return;
        }
        extractContent(json, content);
        extractUsage(json, usage, fallbackModel);
    }

    private void extractContent(JsonNode event, StringBuilder content) {
        JsonNode assistant = firstExisting(event, "assistantResponseEvent", "codeEvent");
        if (assistant != null && assistant.has("content")) {
            content.append(assistant.path("content").asText());
        }
    }

    private void extractUsage(JsonNode event, UsageAccumulator usage, String fallbackModel) {
        JsonNode metadata = firstExisting(event, "messageMetadataEvent", "metadataEvent");
        if (metadata != null) {
            JsonNode tokenUsage = metadata.path("tokenUsage");
            if (!tokenUsage.isMissingNode()) {
                long uncached = tokenUsage.path("uncachedInputTokens").asLong(0);
                long cacheRead = tokenUsage.path("cacheReadInputTokens").asLong(0);
                long cacheWrite = tokenUsage.path("cacheWriteInputTokens").asLong(0);
                long input = uncached + cacheRead + cacheWrite;
                if (input > 0) usage.inputTokens = input;
                if (tokenUsage.has("outputTokens")) usage.outputTokens = tokenUsage.path("outputTokens").asLong(0);
                usage.cacheReadTokens = cacheRead;
                usage.cacheWriteTokens = cacheWrite;
                return;
            }
            if (metadata.has("inputTokens")) usage.inputTokens = metadata.path("inputTokens").asLong();
            if (metadata.has("outputTokens")) usage.outputTokens = metadata.path("outputTokens").asLong();
        }
        JsonNode usageEvent = firstExisting(event, "usageEvent", "usage");
        if (usageEvent != null) {
            if (usageEvent.has("inputTokens")) usage.inputTokens = usageEvent.path("inputTokens").asLong();
            if (usageEvent.has("outputTokens")) usage.outputTokens = usageEvent.path("outputTokens").asLong();
        }
        JsonNode metering = event.path("meteringEvent");
        if (!metering.isMissingNode() && metering.has("usage")) {
            usage.credits = usage.credits.add(metering.path("usage").decimalValue());
        }
        JsonNode contextUsage = event.path("contextUsageEvent");
        if (!contextUsage.isMissingNode() && usage.inputTokens == 0 && contextUsage.has("contextUsagePercentage")) {
            usage.inputTokens = Math.round(contextWindow(fallbackModel) * contextUsage.path("contextUsagePercentage").asDouble() / 100.0);
        }
    }

    private JsonNode firstExisting(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode readJsonOrNull(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (IOException ignored) {
            return null;
        }
    }

    private int readInt(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    private long estimateTokens(byte[] bytes) {
        return Math.max(1, Math.round(bytes.length / 3.5));
    }

    private long contextWindow(String model) {
        if (model != null && model.toLowerCase().contains("haiku")) {
            return 200_000;
        }
        return 200_000;
    }

    private static final class UsageAccumulator {
        private long inputTokens;
        private long outputTokens;
        private BigDecimal credits;
        private long cacheReadTokens;
        private long cacheWriteTokens;
        private long reasoningTokens;

        private UsageAccumulator(
            long inputTokens,
            long outputTokens,
            BigDecimal credits,
            long cacheReadTokens,
            long cacheWriteTokens,
            long reasoningTokens
        ) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.credits = credits;
            this.cacheReadTokens = cacheReadTokens;
            this.cacheWriteTokens = cacheWriteTokens;
            this.reasoningTokens = reasoningTokens;
        }

        private KiroUsage toUsage() {
            return new KiroUsage(inputTokens, outputTokens, credits, cacheReadTokens, cacheWriteTokens, reasoningTokens);
        }
    }
}
