package xyz.star4y.kiroproxy.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AwsEventStreamParserTest {

    private final AwsEventStreamParser parser = new AwsEventStreamParser(new ObjectMapper());

    @Test
    void parsesAssistantContentAndTokenUsage() {
        byte[] bytes = concat(
            event("{\"assistantResponseEvent\":{\"content\":\"hello\"}}"),
            event("{\"messageMetadataEvent\":{\"tokenUsage\":{\"uncachedInputTokens\":7,\"cacheReadInputTokens\":2,\"cacheWriteInputTokens\":1,\"outputTokens\":3}}}")
        );

        KiroCompletionResult result = parser.parse(bytes, "claude-sonnet-4.5", "{}".getBytes(StandardCharsets.UTF_8));

        assertThat(result.content()).isEqualTo("hello");
        assertThat(result.usage().inputTokens()).isEqualTo(10);
        assertThat(result.usage().outputTokens()).isEqualTo(3);
        assertThat(result.usage().cacheReadTokens()).isEqualTo(2);
        assertThat(result.usage().cacheWriteTokens()).isEqualTo(1);
    }

    private byte[] event(String json) {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        int totalLength = 16 + payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(totalLength);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.put(payload);
        buffer.putInt(0);
        return buffer.array();
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
