package xyz.star4y.kiroproxy.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.star4y.kiroproxy.account.AccountPoolService;
import xyz.star4y.kiroproxy.account.ProxyAccountEntity;
import xyz.star4y.kiroproxy.apikey.ApiKeyPrincipal;
import xyz.star4y.kiroproxy.apikey.ApiKeyService;
import xyz.star4y.kiroproxy.common.ApiException;
import xyz.star4y.kiroproxy.model.ModelMappingService;
import xyz.star4y.kiroproxy.stats.StatsService;

@Service
public class ProxyFacade {

    private enum ResponseMode {
        OPENAI_CHAT,
        OPENAI_RESPONSES,
        ANTHROPIC_MESSAGES
    }

    private final ObjectMapper objectMapper;
    private final ApiKeyService apiKeyService;
    private final AccountPoolService accountPoolService;
    private final ModelMappingService modelMappingService;
    private final RateLimiterService rateLimiterService;
    private final KiroPayloadFactory payloadFactory;
    private final KiroUpstreamClient upstreamClient;
    private final OpenAiResponseFactory responseFactory;
    private final StatsService statsService;

    public ProxyFacade(
        ObjectMapper objectMapper,
        ApiKeyService apiKeyService,
        AccountPoolService accountPoolService,
        ModelMappingService modelMappingService,
        RateLimiterService rateLimiterService,
        KiroPayloadFactory payloadFactory,
        KiroUpstreamClient upstreamClient,
        OpenAiResponseFactory responseFactory,
        StatsService statsService
    ) {
        this.objectMapper = objectMapper;
        this.apiKeyService = apiKeyService;
        this.accountPoolService = accountPoolService;
        this.modelMappingService = modelMappingService;
        this.rateLimiterService = rateLimiterService;
        this.payloadFactory = payloadFactory;
        this.upstreamClient = upstreamClient;
        this.responseFactory = responseFactory;
        this.statsService = statsService;
    }

    public Mono<ResponseEntity<?>> chat(JsonNode request, ServerWebExchange exchange) {
        return authenticateAndLimit(exchange)
            .flatMap(principal -> invokeOpenAiChat(request, exchange, principal, "/v1/chat/completions", ResponseMode.OPENAI_CHAT));
    }

    public Mono<ResponseEntity<?>> responses(JsonNode request, ServerWebExchange exchange) {
        ObjectNode chat = responsesToChat(request);
        return authenticateAndLimit(exchange)
            .flatMap(principal -> invokeOpenAiChat(chat, exchange, principal, "/v1/responses", ResponseMode.OPENAI_RESPONSES));
    }

    public Mono<ResponseEntity<?>> messages(JsonNode request, ServerWebExchange exchange) {
        ObjectNode chat = anthropicToChat(request);
        return authenticateAndLimit(exchange)
            .flatMap(principal -> invokeOpenAiChat(chat, exchange, principal, "/v1/messages", ResponseMode.ANTHROPIC_MESSAGES));
    }

    private Mono<ResponseEntity<?>> invokeOpenAiChat(
        JsonNode request,
        ServerWebExchange exchange,
        ApiKeyPrincipal principal,
        String path,
        ResponseMode responseMode
    ) {
        validateChatRequest(request);
        Instant start = Instant.now();
        String requestId = UUID.randomUUID().toString();
        String requestedModel = request.path("model").asText("claude-sonnet-4.5");
        String mappedModel = modelMappingService.apply(requestedModel, principal.keyId());
        String sessionHint = sessionHint(exchange, request, principal.keyId());
        boolean stream = request.path("stream").asBoolean(false);

        return accountPoolService.selectAccount(sessionHint)
            .flatMap(account -> callWithAccount(account, request, mappedModel, sessionHint)
                .flatMap(result -> {
                    Mono<Void> accounting = recordSuccess(requestId, principal, account, path, requestedModel, result, start);
                    ResponseEntity<?> response = stream
                        ? streamResponse(responseMode, mappedModel, result)
                        : jsonResponse(responseMode, request, mappedModel, result);
                    return accounting.thenReturn(response);
                })
                .onErrorResume(error -> recordFailure(requestId, principal, account, path, requestedModel, error, start)
                    .then(Mono.error(mapError(error)))));
    }

    private Mono<KiroCompletionResult> callWithAccount(
        ProxyAccountEntity account,
        JsonNode request,
        String mappedModel,
        String sessionHint
    ) {
        ObjectNode payload = payloadFactory.fromOpenAiChat(request, account, mappedModel, "AI_EDITOR", sessionHint);
        return upstreamClient.complete(account, payload, mappedModel);
    }

    private Mono<ApiKeyPrincipal> authenticateAndLimit(ServerWebExchange exchange) {
        return apiKeyService.authenticate(exchange)
            .flatMap(principal -> {
                RateLimiterService.RateLimitResult limit = rateLimiterService.check(principal.keyId());
                if (!limit.allowed()) {
                    exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(limit.retryAfterMillis() / 1000));
                    return Mono.error(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Rate limit exceeded"));
                }
                return Mono.just(principal);
            });
    }

    private Mono<Void> recordSuccess(
        String requestId,
        ApiKeyPrincipal principal,
        ProxyAccountEntity account,
        String path,
        String model,
        KiroCompletionResult result,
        Instant start
    ) {
        KiroUsage usage = result.usage();
        Mono<Void> accountStats = accountPoolService.recordSuccess(account.getAccountId(), usage.totalTokens());
        Mono<Void> keyStats = apiKeyService.recordUsage(principal.keyId(), usage.credits(), usage.inputTokens(), usage.outputTokens());
        Mono<Void> log = statsService.log(new StatsService.RequestLog(
            requestId,
            principal.keyId(),
            account.getAccountId(),
            path,
            model,
            200,
            true,
            usage.inputTokens(),
            usage.outputTokens(),
            usage.credits(),
            Duration.between(start, Instant.now()).toMillis(),
            null
        ));
        return Mono.whenDelayError(accountStats, keyStats, log).then();
    }

    private Mono<Void> recordFailure(
        String requestId,
        ApiKeyPrincipal principal,
        ProxyAccountEntity account,
        String path,
        String model,
        Throwable error,
        Instant start
    ) {
        Throwable failure = rootFailure(error);
        Integer statusCode = failure instanceof UpstreamException upstream ? upstream.getStatusCode() : null;
        AccountPoolService.UpstreamErrorType type = statusCode != null && (statusCode == 402 || statusCode == 403 || statusCode == 429)
            ? AccountPoolService.UpstreamErrorType.RECOVERABLE
            : AccountPoolService.UpstreamErrorType.FATAL;
        Mono<Void> accountStats = accountPoolService.recordError(account.getAccountId(), type, statusCode);
        Mono<Void> log = statsService.log(new StatsService.RequestLog(
            requestId,
            principal.keyId(),
            account.getAccountId(),
            path,
            model,
            statusCode == null ? 500 : statusCode,
            false,
            0,
            0,
            BigDecimal.ZERO,
            Duration.between(start, Instant.now()).toMillis(),
            failure.getMessage()
        ));
        return Mono.whenDelayError(accountStats, log).then();
    }

    private Throwable mapError(Throwable error) {
        Throwable failure = rootFailure(error);
        if (failure instanceof ApiException) {
            return failure;
        }
        if (failure instanceof UpstreamException upstream) {
            HttpStatus status = upstream.getStatusCode() == 429 || upstream.getStatusCode() == 402
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.BAD_GATEWAY;
            return new ApiException(status, "UPSTREAM_ERROR", upstream.getMessage());
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", failure.getMessage());
    }

    private Throwable rootFailure(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private ResponseEntity<?> jsonResponse(ResponseMode mode, JsonNode request, String mappedModel, KiroCompletionResult result) {
        ObjectNode body = switch (mode) {
            case OPENAI_RESPONSES -> responseFactory.responses(mappedModel, result, request.path("previous_response_id").asText(null));
            case ANTHROPIC_MESSAGES -> responseFactory.anthropicMessage(mappedModel, result);
            case OPENAI_CHAT -> responseFactory.chatCompletion(mappedModel, result);
        };
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Flux<ServerSentEvent<String>>> streamResponse(ResponseMode mode, String mappedModel, KiroCompletionResult result) {
        if (mode == ResponseMode.ANTHROPIC_MESSAGES) {
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(responseFactory.anthropicMessageStream(mappedModel, result));
        }
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(responseFactory.chatStreamEvents(mappedModel, result));
    }

    private ObjectNode responsesToChat(JsonNode request) {
        ObjectNode chat = objectMapper.createObjectNode();
        chat.put("model", request.path("model").asText("claude-sonnet-4.5"));
        if (request.has("temperature")) chat.set("temperature", request.get("temperature"));
        if (request.has("top_p")) chat.set("top_p", request.get("top_p"));
        if (request.has("max_output_tokens")) chat.set("max_tokens", request.get("max_output_tokens"));
        if (request.has("stream")) chat.set("stream", request.get("stream"));
        ArrayNode messages = chat.putArray("messages");
        if (request.hasNonNull("instructions")) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", request.path("instructions").asText());
        }
        JsonNode input = request.path("input");
        if (input.isTextual()) {
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", input.asText());
        } else if (input.isArray()) {
            for (JsonNode item : input) {
                ObjectNode message = messages.addObject();
                message.put("role", item.path("role").asText("user"));
                message.set("content", normalizeResponsesContent(item.path("content")));
            }
        }
        return chat;
    }

    private ObjectNode anthropicToChat(JsonNode request) {
        ObjectNode chat = objectMapper.createObjectNode();
        chat.put("model", request.path("model").asText("claude-sonnet-4.5"));
        if (request.has("max_tokens")) chat.set("max_tokens", request.get("max_tokens"));
        if (request.has("temperature")) chat.set("temperature", request.get("temperature"));
        if (request.has("top_p")) chat.set("top_p", request.get("top_p"));
        if (request.has("stream")) chat.set("stream", request.get("stream"));
        ArrayNode messages = chat.putArray("messages");
        if (request.hasNonNull("system")) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", anthropicContentText(request.path("system")));
        }
        JsonNode inputMessages = request.path("messages");
        if (inputMessages.isArray()) {
            for (JsonNode item : inputMessages) {
                ObjectNode message = messages.addObject();
                String role = item.path("role").asText("user");
                message.put("role", "assistant".equals(role) ? "assistant" : "user");
                message.put("content", anthropicContentText(item.path("content")));
            }
        }
        if (request.path("tools").isArray() && !request.path("tools").isEmpty()) {
            chat.set("tools", anthropicToolsToOpenAi(request.path("tools")));
        }
        return chat;
    }

    private JsonNode normalizeResponsesContent(JsonNode content) {
        if (!content.isArray()) {
            return content;
        }
        ArrayNode normalized = objectMapper.createArrayNode();
        for (JsonNode part : content) {
            if ("input_text".equals(part.path("type").asText()) || "output_text".equals(part.path("type").asText())) {
                ObjectNode item = normalized.addObject();
                item.put("type", "text");
                item.put("text", part.path("text").asText());
            }
        }
        return normalized;
    }

    private String anthropicContentText(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (!content.isArray()) {
            return content.toString();
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : content) {
            String type = part.path("type").asText();
            String text = switch (type) {
                case "text" -> part.path("text").asText("");
                case "tool_result" -> anthropicToolResultText(part);
                case "tool_use" -> anthropicToolUseText(part);
                default -> "";
            };
            if (!text.isBlank()) {
                if (!builder.isEmpty()) builder.append("\n");
                builder.append(text);
            }
        }
        return builder.toString();
    }

    private String anthropicToolResultText(JsonNode part) {
        JsonNode content = part.path("content");
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            return anthropicContentText(content);
        }
        return content.isMissingNode() || content.isNull() ? "" : content.toString();
    }

    private String anthropicToolUseText(JsonNode part) {
        String name = part.path("name").asText("");
        JsonNode input = part.path("input");
        if (input.isMissingNode() || input.isNull() || input.isEmpty()) {
            return "Tool use: " + name;
        }
        return "Tool use: " + name + "\nInput: " + input;
    }

    private ArrayNode anthropicToolsToOpenAi(JsonNode tools) {
        ArrayNode converted = objectMapper.createArrayNode();
        for (JsonNode tool : tools) {
            ObjectNode wrapper = converted.addObject();
            wrapper.put("type", "function");
            ObjectNode function = wrapper.putObject("function");
            function.put("name", tool.path("name").asText());
            function.put("description", tool.path("description").asText(""));
            function.set("parameters", tool.path("input_schema"));
        }
        return converted;
    }

    private void validateChatRequest(JsonNode request) {
        if (!request.hasNonNull("model")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_REQUIRED", "model is required");
        }
        if (!request.path("messages").isArray() || request.path("messages").isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MESSAGES_REQUIRED", "messages must be a non-empty array");
        }
    }

    private String sessionHint(ServerWebExchange exchange, JsonNode body, String keyId) {
        String header = firstNonBlank(
            exchange.getRequest().getHeaders().getFirst("x-claude-code-session-id"),
            exchange.getRequest().getHeaders().getFirst("x-opencode-session"),
            exchange.getRequest().getHeaders().getFirst("x-session-affinity"),
            exchange.getRequest().getHeaders().getFirst("x-conversation-id")
        );
        String bodyHint = firstNonBlank(
            body.path("conversation_id").asText(null),
            body.path("conversationId").asText(null),
            body.path("thread_id").asText(null),
            body.path("session_id").asText(null)
        );
        String hint = firstNonBlank(header, bodyHint);
        return hint == null ? null : keyId + ":" + hint;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
