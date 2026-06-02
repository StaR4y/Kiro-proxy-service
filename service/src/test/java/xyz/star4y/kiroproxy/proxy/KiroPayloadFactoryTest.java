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

    @Test
    void usesDefaultProfileArnWhenAccountDoesNotProvideOne() throws Exception {
        JsonNode request = objectMapper.readTree("""
            {
              "model": "simple-task",
              "messages": [
                {"role": "user", "content": "ping"}
              ]
            }
            """);
        ProxyAccountEntity account = new ProxyAccountEntity();
        account.setProvider("BuilderId");

        ObjectNode payload = factory.fromOpenAiChat(request, account, "simple-task", "AI_EDITOR", "session-1");

        assertThat(payload.path("profileArn").asText())
            .isEqualTo("arn:aws:codewhisperer:us-east-1:638616132270:profile/AAAACCCCXXXX");
    }

    @Test
    void preservesAssistantToolCallsAndToolResultsInHistory() throws Exception {
        JsonNode request = objectMapper.readTree("""
            {
              "model": "gpt-4o",
              "messages": [
                {"role": "user", "content": "list files"},
                {
                  "role": "assistant",
                  "content": null,
                  "tool_calls": [
                    {
                      "id": "toolu_1",
                      "type": "function",
                      "function": {
                        "name": "Bash",
                        "arguments": "{\\"command\\":\\"ls\\"}"
                      }
                    }
                  ]
                },
                {
                  "role": "tool",
                  "tool_call_id": "toolu_1",
                  "content": "README.md"
                },
                {"role": "user", "content": "summarize"}
              ]
            }
            """);
        ProxyAccountEntity account = new ProxyAccountEntity();
        account.setProfileArn("arn:test");

        ObjectNode payload = factory.fromOpenAiChat(request, account, "gpt-4o", "AI_EDITOR", "session-1");

        JsonNode history = payload.path("conversationState").path("history");
        JsonNode toolUse = history.get(1).path("assistantResponseMessage").path("toolUses").get(0);
        assertThat(toolUse.path("toolUseId").asText()).isEqualTo("toolu_1");
        assertThat(toolUse.path("name").asText()).isEqualTo("Bash");
        assertThat(toolUse.path("input").asText()).isEqualTo("{\"command\":\"ls\"}");

        JsonNode toolResult = history.get(2)
            .path("userInputMessage")
            .path("userInputMessageContext")
            .path("toolResults")
            .get(0);
        assertThat(toolResult.path("toolUseId").asText()).isEqualTo("toolu_1");
        assertThat(toolResult.path("content").get(0).path("text").asText()).isEqualTo("README.md");
        assertThat(toolResult.path("status").asText()).isEqualTo("success");
    }
}
