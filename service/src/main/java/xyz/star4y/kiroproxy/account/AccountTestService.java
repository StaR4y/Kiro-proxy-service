package xyz.star4y.kiroproxy.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.star4y.kiroproxy.proxy.KiroPayloadFactory;
import xyz.star4y.kiroproxy.proxy.KiroUpstreamClient;
import xyz.star4y.kiroproxy.proxy.UpstreamException;

@Service
public class AccountTestService {

    private static final int DEFAULT_CONCURRENCY = 3;
    private static final int MAX_CONCURRENCY = 8;
    private static final String DEFAULT_MODEL = "auto";
    private static final String DEFAULT_PROMPT = "Reply OK in one short sentence.";

    private final ObjectMapper objectMapper;
    private final ProxyAccountRepository repository;
    private final AccountPoolService accountPoolService;
    private final KiroPayloadFactory payloadFactory;
    private final KiroUpstreamClient upstreamClient;

    public AccountTestService(
        ObjectMapper objectMapper,
        ProxyAccountRepository repository,
        AccountPoolService accountPoolService,
        KiroPayloadFactory payloadFactory,
        KiroUpstreamClient upstreamClient
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.accountPoolService = accountPoolService;
        this.payloadFactory = payloadFactory;
        this.upstreamClient = upstreamClient;
    }

    public Mono<AccountDtos.TestAccountsResponse> test(AccountDtos.TestAccountsRequest request) {
        AccountDtos.TestAccountsRequest normalized = request == null
            ? new AccountDtos.TestAccountsRequest(null, true, null, null, null)
            : request;
        Instant startedAt = Instant.now();
        int concurrency = clamp(Optional.ofNullable(normalized.maxConcurrency()).orElse(DEFAULT_CONCURRENCY), 1, MAX_CONCURRENCY);

        return targets(normalized)
            .flatMap(target -> testTarget(target, normalized), concurrency)
            .collectList()
            .map(items -> summarize(startedAt, items));
    }

    private Flux<AccountTarget> targets(AccountDtos.TestAccountsRequest request) {
        List<String> ids = request.accountIds() == null
            ? List.of()
            : request.accountIds().stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (ids.isEmpty()) {
            return repository.findAll().map(AccountTarget::found);
        }
        return Flux.fromIterable(ids)
            .flatMap(id -> repository.findByAccountId(id)
                .map(AccountTarget::found)
                .defaultIfEmpty(AccountTarget.missing(id)));
    }

    private Mono<AccountDtos.TestAccountItemResponse> testTarget(AccountTarget target, AccountDtos.TestAccountsRequest request) {
        if (target.account() == null) {
            return Mono.just(item(target.accountId(), null, "FAILED", "Account not found", "Account not found", 404, 0L, Instant.now(), firstNonBlank(request.model(), DEFAULT_MODEL)));
        }

        ProxyAccountEntity account = target.account();
        String model = firstNonBlank(request.model(), DEFAULT_MODEL);
        String skipReason = skipReason(account, request);
        if (skipReason != null) {
            return Mono.just(item(account.getAccountId(), account.getEmail(), "SKIPPED", skipReason, skipReason, null, 0L, Instant.now(), model));
        }

        Instant startedAt = Instant.now();
        ObjectNode chat = testChatRequest(model, firstNonBlank(request.prompt(), DEFAULT_PROMPT));
        ObjectNode payload = payloadFactory.fromOpenAiChat(
            chat,
            account,
            model,
            "AI_EDITOR",
            "admin-test:" + account.getAccountId() + ":" + UUID.randomUUID()
        );

        return upstreamClient.complete(account, payload, model)
            .flatMap(result -> accountPoolService.recordSuccess(account.getAccountId(), result.usage().totalTokens())
                .thenReturn(item(
                    account.getAccountId(),
                    account.getEmail(),
                    "SUCCESS",
                    truncate(firstNonBlank(result.content(), "OK")),
                    firstNonBlank(result.content(), "OK"),
                    200,
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    Instant.now(),
                    model
                )))
            .onErrorResume(error -> {
                Throwable failure = rootFailure(error);
                int statusCode = statusCode(failure);
                String detail = firstNonBlank(failure.getMessage(), failure.getClass().getName());
                AccountPoolService.UpstreamErrorType type = recoverableStatus(statusCode)
                    ? AccountPoolService.UpstreamErrorType.RECOVERABLE
                    : AccountPoolService.UpstreamErrorType.FATAL;
                return accountPoolService.recordError(account.getAccountId(), type, statusCode == 0 ? null : statusCode)
                    .thenReturn(item(
                        account.getAccountId(),
                        account.getEmail(),
                        "FAILED",
                        truncate(detail),
                        detail,
                        statusCode == 0 ? null : statusCode,
                        Duration.between(startedAt, Instant.now()).toMillis(),
                        Instant.now(),
                        model
                    ));
            });
    }

    private ObjectNode testChatRequest(String model, String prompt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", 24);
        ObjectNode message = root.putArray("messages").addObject();
        message.put("role", "user");
        message.put("content", prompt);
        return root;
    }

    private String skipReason(ProxyAccountEntity account, AccountDtos.TestAccountsRequest request) {
        if (account.getAccessToken() == null || account.getAccessToken().isBlank()) {
            return "Access token is empty";
        }
        boolean onlyEnabled = !Boolean.FALSE.equals(request.onlyEnabled());
        if (!onlyEnabled) {
            return null;
        }
        if (Boolean.FALSE.equals(account.getEnabled())) {
            return "Account is disabled";
        }
        if (account.getSuspendedAt() != null) {
            return "Account is suspended";
        }
        if (account.getQuotaExhaustedAt() != null) {
            return "Quota is exhausted";
        }
        Long limit = account.getQuotaLimit();
        Long used = account.getQuotaUsed();
        if (limit != null && limit > 0 && used != null && used >= limit) {
            return "Quota limit reached";
        }
        return null;
    }

    private AccountDtos.TestAccountsResponse summarize(
        Instant startedAt,
        List<AccountDtos.TestAccountItemResponse> items
    ) {
        int success = count(items, "SUCCESS");
        int failed = count(items, "FAILED");
        int skipped = count(items, "SKIPPED");
        return new AccountDtos.TestAccountsResponse(
            items.size(),
            success,
            failed,
            skipped,
            startedAt,
            Instant.now(),
            items
        );
    }

    private int count(List<AccountDtos.TestAccountItemResponse> items, String status) {
        return (int) items.stream().filter(item -> status.equals(item.status())).count();
    }

    private AccountDtos.TestAccountItemResponse item(
        String accountId,
        String email,
        String status,
        String message,
        String detail,
        Integer statusCode,
        Long latencyMs,
        Instant testedAt,
        String model
    ) {
        return new AccountDtos.TestAccountItemResponse(accountId, email, status, message, detail, statusCode, latencyMs, testedAt, model);
    }

    private int statusCode(Throwable error) {
        return error instanceof UpstreamException upstream ? upstream.getStatusCode() : 0;
    }

    private Throwable rootFailure(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private boolean recoverableStatus(int statusCode) {
        return statusCode == 402 || statusCode == 403 || statusCode == 429;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 180 ? singleLine : singleLine.substring(0, 180) + "...";
    }

    private record AccountTarget(String accountId, ProxyAccountEntity account) {
        static AccountTarget found(ProxyAccountEntity account) {
            return new AccountTarget(account.getAccountId(), account);
        }

        static AccountTarget missing(String accountId) {
            return new AccountTarget(accountId, null);
        }
    }
}
