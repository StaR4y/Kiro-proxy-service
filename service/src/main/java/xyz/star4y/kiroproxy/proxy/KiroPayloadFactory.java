package xyz.star4y.kiroproxy.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.star4y.kiroproxy.account.ProxyAccountEntity;

@Component
public class KiroPayloadFactory {

    private static final String BUILDER_ID_PROFILE_ARN = "arn:aws:codewhisperer:us-east-1:638616132270:profile/AAAACCCCXXXX";
    private static final String SOCIAL_PROFILE_ARN = "arn:aws:codewhisperer:us-east-1:699475941385:profile/EHGA3GRVQMUK";

    private final ObjectMapper objectMapper;

    public KiroPayloadFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode fromOpenAiChat(JsonNode request, ProxyAccountEntity account, String model, String origin, String sessionHint) {
        ArrayNode messages = request.withArray("messages");
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode conversationState = payload.putObject("conversationState");
        conversationState.put("agentContinuationId", UUID.randomUUID().toString());
        conversationState.put("agentTaskType", "vibe");
        conversationState.put("chatTriggerType", "MANUAL");
        conversationState.put("conversationId", stableConversationId(request, sessionHint));

        ArrayNode history = objectMapper.createArrayNode();
        for (int i = 0; i < Math.max(0, messages.size() - 1); i++) {
            ObjectNode historyMessage = historyMessage(messages.get(i), model, origin);
            if (!historyMessage.isEmpty()) {
                history.add(historyMessage);
            }
        }
        if (!history.isEmpty()) {
            conversationState.set("history", history);
        }

        JsonNode current = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        ObjectNode currentMessage = conversationState.putObject("currentMessage");
        currentMessage.set("userInputMessage", userInput(current, model, origin, request.get("tools")));

        ObjectNode inferenceConfig = objectMapper.createObjectNode();
        if (request.has("max_tokens")) {
            inferenceConfig.set("maxTokens", request.get("max_tokens"));
        }
        if (request.has("temperature")) {
            inferenceConfig.set("temperature", request.get("temperature"));
        }
        if (request.has("top_p")) {
            inferenceConfig.set("topP", request.get("top_p"));
        }
        if (!inferenceConfig.isEmpty()) {
            payload.set("inferenceConfig", inferenceConfig);
        }
        payload.put("profileArn", profileArn(account));
        if (request.has("thinking")) {
            ObjectNode additional = payload.putObject("additionalModelRequestFields");
            additional.set("thinking", request.get("thinking"));
        }
        return payload;
    }

    private ObjectNode historyMessage(JsonNode message, String model, String origin) {
        ObjectNode node = objectMapper.createObjectNode();
        String role = message.path("role").asText("user");
        if ("assistant".equals(role)) {
            ObjectNode assistant = node.putObject("assistantResponseMessage");
            assistant.put("content", contentToText(message.path("content")));
        } else if ("user".equals(role) || "system".equals(role) || "tool".equals(role)) {
            node.set("userInputMessage", userInput(message, model, origin, null));
        }
        return node;
    }

    private ObjectNode userInput(JsonNode message, String model, String origin, JsonNode tools) {
        ObjectNode userInput = objectMapper.createObjectNode();
        String content = message == null ? "Continue" : contentToText(message.path("content"));
        userInput.put("content", content.isBlank() ? "Continue" : content);
        userInput.put("modelId", KiroModelMapper.map(model));
        userInput.put("origin", origin);
        if (tools != null && tools.isArray() && !tools.isEmpty()) {
            ObjectNode context = userInput.putObject("userInputMessageContext");
            ArrayNode kiroTools = context.putArray("tools");
            for (JsonNode tool : tools) {
                JsonNode function = tool.path("function");
                if (function.isMissingNode()) {
                    continue;
                }
                ObjectNode wrapper = kiroTools.addObject();
                ObjectNode spec = wrapper.putObject("toolSpecification");
                spec.put("name", function.path("name").asText());
                spec.put("description", function.path("description").asText(""));
                ObjectNode inputSchema = spec.putObject("inputSchema");
                inputSchema.set("json", function.path("parameters"));
            }
        }
        return userInput;
    }

    private String contentToText(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode part : content) {
                if ("text".equals(part.path("type").asText()) || "input_text".equals(part.path("type").asText())) {
                    if (!builder.isEmpty()) builder.append("\n");
                    builder.append(part.path("text").asText());
                }
            }
            return builder.toString();
        }
        return content.toString();
    }

    private String stableConversationId(JsonNode request, String sessionHint) {
        String explicit = firstNonBlank(
            request.path("conversation_id").asText(null),
            request.path("conversationId").asText(null),
            sessionHint
        );
        return explicit == null ? UUID.randomUUID().toString() : UUID.nameUUIDFromBytes(explicit.getBytes()).toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String profileArn(ProxyAccountEntity account) {
        if (account.getProfileArn() != null && !account.getProfileArn().isBlank()) {
            return account.getProfileArn();
        }
        String provider = account.getProvider();
        if ("Github".equalsIgnoreCase(provider) || "Google".equalsIgnoreCase(provider)) {
            return SOCIAL_PROFILE_ARN;
        }
        return BUILDER_ID_PROFILE_ARN;
    }
}
