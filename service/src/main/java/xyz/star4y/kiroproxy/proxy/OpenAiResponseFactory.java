package xyz.star4y.kiroproxy.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OpenAiResponseFactory {

    private final ObjectMapper objectMapper;

    public OpenAiResponseFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode chatCompletion(String model, KiroCompletionResult result) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "chatcmpl-" + UUID.randomUUID());
        root.put("object", "chat.completion");
        root.put("created", Instant.now().getEpochSecond());
        root.put("model", model);
        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", result.content());
        choice.put("finish_reason", "stop");
        appendUsage(root, result.usage(), "prompt_tokens", "completion_tokens");
        return root;
    }

    public ObjectNode responses(String model, KiroCompletionResult result, String previousResponseId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "resp-" + UUID.randomUUID());
        root.put("object", "response");
        root.put("created_at", Instant.now().getEpochSecond());
        root.put("model", model);
        if (previousResponseId != null) {
            root.put("previous_response_id", previousResponseId);
        }
        ArrayNode output = root.putArray("output");
        ObjectNode item = output.addObject();
        item.put("type", "message");
        item.put("id", "msg-" + UUID.randomUUID());
        item.put("role", "assistant");
        ArrayNode content = item.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "output_text");
        text.put("text", result.content());
        appendUsage(root, result.usage(), "input_tokens", "output_tokens");
        return root;
    }

    public String chatStreamChunk(String model, String content) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "chatcmpl-" + UUID.randomUUID());
        root.put("object", "chat.completion.chunk");
        root.put("created", Instant.now().getEpochSecond());
        root.put("model", model);
        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        ObjectNode delta = choice.putObject("delta");
        delta.put("role", "assistant");
        delta.put("content", content);
        choice.putNull("finish_reason");
        return "data: " + root + "\n\n";
    }

    public String chatStreamStop(String model) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "chatcmpl-" + UUID.randomUUID());
        root.put("object", "chat.completion.chunk");
        root.put("created", Instant.now().getEpochSecond());
        root.put("model", model);
        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        choice.putObject("delta");
        choice.put("finish_reason", "stop");
        return "data: " + root + "\n\ndata: [DONE]\n\n";
    }

    private void appendUsage(ObjectNode root, KiroUsage usage, String inputName, String outputName) {
        ObjectNode usageNode = root.putObject("usage");
        usageNode.put(inputName, usage.inputTokens());
        usageNode.put(outputName, usage.outputTokens());
        usageNode.put("total_tokens", usage.totalTokens());
        ObjectNode inputDetails = usageNode.putObject(inputName + "_details");
        inputDetails.put("cached_tokens", usage.cacheReadTokens());
        ObjectNode outputDetails = usageNode.putObject(outputName + "_details");
        outputDetails.put("reasoning_tokens", usage.reasoningTokens());
    }
}
