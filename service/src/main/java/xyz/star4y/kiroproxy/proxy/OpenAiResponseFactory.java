package xyz.star4y.kiroproxy.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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
        if (result.toolUses().isEmpty()) {
            message.put("content", result.content());
            choice.put("finish_reason", "stop");
        } else {
            message.put("content", result.content().isBlank() ? null : result.content());
            ArrayNode toolCalls = message.putArray("tool_calls");
            for (KiroToolUse toolUse : result.toolUses()) {
                ObjectNode call = toolCalls.addObject();
                call.put("id", toolUse.id());
                call.put("type", "function");
                ObjectNode function = call.putObject("function");
                function.put("name", toolUse.name());
                function.put("arguments", toolUse.input().toString());
            }
            choice.put("finish_reason", "tool_calls");
        }
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
        if (result.toolUses().isEmpty()) {
            ObjectNode item = output.addObject();
            item.put("type", "message");
            item.put("id", "msg-" + UUID.randomUUID());
            item.put("role", "assistant");
            ArrayNode content = item.putArray("content");
            ObjectNode text = content.addObject();
            text.put("type", "output_text");
            text.put("text", result.content());
        } else {
            if (!result.content().isBlank()) {
                ObjectNode item = output.addObject();
                item.put("type", "message");
                item.put("id", "msg-" + UUID.randomUUID());
                item.put("role", "assistant");
                ArrayNode content = item.putArray("content");
                ObjectNode text = content.addObject();
                text.put("type", "output_text");
                text.put("text", result.content());
            }
            for (KiroToolUse toolUse : result.toolUses()) {
                ObjectNode call = output.addObject();
                call.put("type", "function_call");
                call.put("id", "fc-" + UUID.randomUUID());
                call.put("call_id", toolUse.id());
                call.put("name", toolUse.name());
                call.put("arguments", toolUse.input().toString());
                call.put("status", "completed");
            }
        }
        appendUsage(root, result.usage(), "input_tokens", "output_tokens");
        return root;
    }

    public ObjectNode anthropicMessage(String model, KiroCompletionResult result) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "msg_" + UUID.randomUUID());
        root.put("type", "message");
        root.put("role", "assistant");
        root.put("model", model);
        ArrayNode content = root.putArray("content");
        appendAnthropicContentBlocks(content, result);
        root.put("stop_reason", result.toolUses().isEmpty() ? "end_turn" : "tool_use");
        root.putNull("stop_sequence");
        ObjectNode usage = root.putObject("usage");
        usage.put("input_tokens", result.usage().inputTokens());
        usage.put("output_tokens", result.usage().outputTokens());
        return root;
    }

    public String chatStreamChunk(String model, String content) {
        return "data: " + chatStreamChunkBody(model, content) + "\n\n";
    }

    public String chatStreamStop(String model) {
        return "data: " + chatStreamStopBody(model) + "\n\ndata: [DONE]\n\n";
    }

    public Flux<ServerSentEvent<String>> chatStreamEvents(String model, KiroCompletionResult result) {
        if (!result.toolUses().isEmpty()) {
            return chatToolStreamEvents(model, result);
        }
        return Flux.just(
            ServerSentEvent.builder(chatStreamChunkBody(model, result.content()).toString()).build(),
            ServerSentEvent.builder(chatStreamStopBody(model).toString()).build(),
            ServerSentEvent.builder("[DONE]").build()
        );
    }

    private ObjectNode chatStreamChunkBody(String model, String content) {
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
        return root;
    }

    private ObjectNode chatStreamStopBody(String model) {
        return chatStreamStopBody(model, "stop");
    }

    private ObjectNode chatStreamStopBody(String model, String finishReason) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "chatcmpl-" + UUID.randomUUID());
        root.put("object", "chat.completion.chunk");
        root.put("created", Instant.now().getEpochSecond());
        root.put("model", model);
        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        choice.putObject("delta");
        choice.put("finish_reason", finishReason);
        return root;
    }

    private Flux<ServerSentEvent<String>> chatToolStreamEvents(String model, KiroCompletionResult result) {
        List<ServerSentEvent<String>> events = new ArrayList<>();
        String content = result.content();
        if (!content.isBlank()) {
            events.add(ServerSentEvent.builder(chatStreamChunkBody(model, content).toString()).build());
        }
        for (int i = 0; i < result.toolUses().size(); i++) {
            events.add(ServerSentEvent.builder(chatToolCallChunkBody(model, i, result.toolUses().get(i)).toString()).build());
        }
        events.add(ServerSentEvent.builder(chatStreamStopBody(model, "tool_calls").toString()).build());
        events.add(ServerSentEvent.builder("[DONE]").build());
        return Flux.fromIterable(events);
    }

    private ObjectNode chatToolCallChunkBody(String model, int index, KiroToolUse toolUse) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "chatcmpl-" + UUID.randomUUID());
        root.put("object", "chat.completion.chunk");
        root.put("created", Instant.now().getEpochSecond());
        root.put("model", model);
        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        ObjectNode delta = choice.putObject("delta");
        ArrayNode toolCalls = delta.putArray("tool_calls");
        ObjectNode call = toolCalls.addObject();
        call.put("index", index);
        call.put("id", toolUse.id());
        call.put("type", "function");
        ObjectNode function = call.putObject("function");
        function.put("name", toolUse.name());
        function.put("arguments", toolUse.input().toString());
        choice.putNull("finish_reason");
        return root;
    }

    public Flux<ServerSentEvent<String>> anthropicMessageStream(String model, KiroCompletionResult result) {
        String messageId = "msg_" + UUID.randomUUID();
        ObjectNode start = objectMapper.createObjectNode();
        start.put("type", "message_start");
        ObjectNode message = start.putObject("message");
        message.put("id", messageId);
        message.put("type", "message");
        message.put("role", "assistant");
        message.put("model", model);
        message.putArray("content");
        message.putNull("stop_reason");
        message.putNull("stop_sequence");
        ObjectNode startUsage = message.putObject("usage");
        startUsage.put("input_tokens", result.usage().inputTokens());
        startUsage.put("output_tokens", 0);

        List<ServerSentEvent<String>> events = new ArrayList<>();
        events.add(sse("message_start", start));
        int index = 0;
        if (!result.content().isBlank() || result.toolUses().isEmpty()) {
            events.add(textBlockStart(index));
            events.add(textBlockDelta(index, result.content()));
            events.add(blockStop(index));
            index++;
        }
        for (KiroToolUse toolUse : result.toolUses()) {
            events.add(toolBlockStart(index, toolUse));
            events.add(toolInputDelta(index, toolUse));
            events.add(blockStop(index));
            index++;
        }

        ObjectNode messageDelta = objectMapper.createObjectNode();
        messageDelta.put("type", "message_delta");
        ObjectNode stopDelta = messageDelta.putObject("delta");
        stopDelta.put("stop_reason", result.toolUses().isEmpty() ? "end_turn" : "tool_use");
        stopDelta.putNull("stop_sequence");
        ObjectNode deltaUsage = messageDelta.putObject("usage");
        deltaUsage.put("output_tokens", result.usage().outputTokens());

        ObjectNode messageStop = objectMapper.createObjectNode();
        messageStop.put("type", "message_stop");

        events.add(sse("message_delta", messageDelta));
        events.add(sse("message_stop", messageStop));
        return Flux.fromIterable(events);
    }

    private void appendAnthropicContentBlocks(ArrayNode content, KiroCompletionResult result) {
        if (!result.content().isBlank() || result.toolUses().isEmpty()) {
            ObjectNode text = content.addObject();
            text.put("type", "text");
            text.put("text", result.content());
        }
        for (KiroToolUse toolUse : result.toolUses()) {
            ObjectNode block = content.addObject();
            block.put("type", "tool_use");
            block.put("id", toolUse.id());
            block.put("name", toolUse.name());
            block.set("input", toolUse.input());
        }
    }

    private ServerSentEvent<String> textBlockStart(int index) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", "content_block_start");
        event.put("index", index);
        ObjectNode block = event.putObject("content_block");
        block.put("type", "text");
        block.put("text", "");
        return sse("content_block_start", event);
    }

    private ServerSentEvent<String> textBlockDelta(int index, String content) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", "content_block_delta");
        event.put("index", index);
        ObjectNode delta = event.putObject("delta");
        delta.put("type", "text_delta");
        delta.put("text", content);
        return sse("content_block_delta", event);
    }

    private ServerSentEvent<String> toolBlockStart(int index, KiroToolUse toolUse) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", "content_block_start");
        event.put("index", index);
        ObjectNode block = event.putObject("content_block");
        block.put("type", "tool_use");
        block.put("id", toolUse.id());
        block.put("name", toolUse.name());
        block.set("input", objectMapper.createObjectNode());
        return sse("content_block_start", event);
    }

    private ServerSentEvent<String> toolInputDelta(int index, KiroToolUse toolUse) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", "content_block_delta");
        event.put("index", index);
        ObjectNode delta = event.putObject("delta");
        delta.put("type", "input_json_delta");
        delta.put("partial_json", toolUse.input().toString());
        return sse("content_block_delta", event);
    }

    private ServerSentEvent<String> blockStop(int index) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", "content_block_stop");
        event.put("index", index);
        return sse("content_block_stop", event);
    }

    private ServerSentEvent<String> sse(String event, ObjectNode data) {
        return ServerSentEvent.builder(data.toString()).event(event).build();
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
