package xyz.star4y.kiroproxy.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record CreateAccountRequest(
        String accountId,
        String email,
        @NotBlank String accessToken,
        String refreshToken,
        String clientId,
        String clientSecret,
        String region,
        String authMethod,
        String provider,
        String profileArn,
        String machineId,
        String proxyUrl,
        Long quotaLimit
    ) {
    }

    public record UpdateAccountRequest(
        String email,
        String accessToken,
        String refreshToken,
        String clientId,
        String clientSecret,
        String region,
        String authMethod,
        String provider,
        String profileArn,
        String machineId,
        String proxyUrl,
        Boolean enabled,
        Long quotaUsed,
        Long quotaLimit,
        Instant quotaResetAt
    ) {
    }

    public record SuspendAccountRequest(
        @NotBlank String reason,
        String message
    ) {
    }

    public record TestAccountsRequest(
        List<String> accountIds,
        Boolean onlyEnabled,
        String model,
        String prompt,
        Integer maxConcurrency
    ) {
    }

    public record ImportAccountsRequest(
        @NotEmpty List<@Valid CreateAccountRequest> accounts,
        Boolean upsert
    ) {
    }

    public record ImportAccountsResponse(
        Integer total,
        Integer created,
        Integer updated,
        Integer skipped,
        Integer failed,
        List<ImportAccountItemResponse> items
    ) {
    }

    public record ImportAccountItemResponse(
        Integer index,
        String accountId,
        String email,
        String status,
        String message,
        AccountResponse account
    ) {
    }

    public record TestAccountsResponse(
        Integer total,
        Integer success,
        Integer failed,
        Integer skipped,
        Instant startedAt,
        Instant finishedAt,
        List<TestAccountItemResponse> items
    ) {
    }

    public record TestAccountItemResponse(
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
    }

    public record AccountResponse(
        String accountId,
        String email,
        String region,
        String authMethod,
        String provider,
        String profileArn,
        String machineId,
        String proxyUrl,
        Boolean enabled,
        Long requestCount,
        Integer errorCount,
        Long quotaUsed,
        Long quotaLimit,
        Instant quotaExhaustedAt,
        Instant quotaResetAt,
        Instant suspendedAt,
        String suspendReason,
        String suspendMessage,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
