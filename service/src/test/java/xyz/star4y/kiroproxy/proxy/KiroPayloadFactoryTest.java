package xyz.star4y.kiroproxy.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import xyz.star4y.kiroproxy.account.ProxyAccountEntity;

class KiroPayloadFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KiroPayloadFactory factory = new KiroPayloadFactory(objectMapper);

    @Test
    void buildsConversationPayloadFromOpenAiMessages() throws Exception {
        JsonNode request = objectMapper.readTree("""
            {
              "model": "gpt-4o",
              "messages": [
                {"role": "system", "content": "system prompt"},
                {"role": "user", "content": "hello"}
              ],
              "max_tokens": 128
            }
            """);
        ProxyAccountEntity account = new ProxyAccountEntity();
        account.setProfileArn("arn:test");

        ObjectNode payload = factory.fromOpenAiChat(request, account, "gpt-4o", "AI_EDITOR", "session-1");

        assertThat(payload.path("profileArn").asText()).isEqualTo("arn:test");
        assertThat(payload.path("conversationState").path("history")).hasSize(1);
        assertThat(payload.path("conversationState").path("currentMessage").path("userInputMessage").path("content").asText())
            .isEqualTo("hello");
        assertThat(payload.path("conversationState").path("currentMessage").path("userInputMessage").path("modelId").asText())
            .isEqualTo("claude-sonnet-4.5");
        assertThat(payload.path("inferenceConfig").path("maxTokens").asInt()).isEqualTo(128);
    }
}
