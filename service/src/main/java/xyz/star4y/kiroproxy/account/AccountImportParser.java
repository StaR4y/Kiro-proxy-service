package xyz.star4y.kiroproxy.account;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import xyz.star4y.kiroproxy.common.ApiException;

@Component
public class AccountImportParser {

    public AccountDtos.ImportAccountsRequest parse(JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            throw invalid("Account import body is required");
        }

        JsonNode accountsNode = body.isArray() ? body : body.path("accounts");
        if (!accountsNode.isArray() || accountsNode.isEmpty()) {
            throw invalid("Account import body must be an array or an object with accounts array");
        }

        List<AccountDtos.CreateAccountRequest> accounts = new ArrayList<>(accountsNode.size());
        for (JsonNode item : accountsNode) {
            accounts.add(parseAccount(item));
        }
        return new AccountDtos.ImportAccountsRequest(accounts, booleanValue(body.path("upsert")));
    }

    private AccountDtos.CreateAccountRequest parseAccount(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }

        JsonNode credentials = item.path("credentials");
        JsonNode usage = item.path("usage");
        String provider = firstText(item, credentials, "provider", "idp");
        String authMethod = normalizeAuthMethod(firstText(item, credentials, "authMethod", "auth_method"), provider);

        return new AccountDtos.CreateAccountRequest(
            firstText(item, null, "accountId", "account_id", "id"),
            text(item, "email"),
            firstText(item, credentials, "accessToken", "access_token", "token"),
            firstText(item, credentials, "refreshToken", "refresh_token"),
            firstText(item, credentials, "clientId", "client_id"),
            firstText(item, credentials, "clientSecret", "client_secret"),
            firstText(item, credentials, "region"),
            authMethod,
            normalizeProvider(provider),
            firstText(item, null, "profileArn", "profile_arn"),
            firstText(item, null, "machineId", "machine_id"),
            firstText(item, null, "proxyUrl", "proxy_url"),
            firstLong(item.path("quotaLimit"), item.path("quota_limit"), usage.path("limit"))
        );
    }

    private String normalizeAuthMethod(String value, String provider) {
        String method = clean(value);
        if (method == null) {
            String normalizedProvider = normalizeProvider(provider);
            if (normalizedProvider == null) {
                return null;
            }
            String lower = normalizedProvider.toLowerCase();
            return (lower.contains("builder") || lower.contains("enterprise") || lower.contains("sso") || lower.contains("idc"))
                ? "idc"
                : "social";
        }
        return "idc".equalsIgnoreCase(method) || "idc".equals(method.replaceAll("[^A-Za-z0-9]", ""))
            ? "idc"
            : method.toLowerCase();
    }

    private String normalizeProvider(String value) {
        String provider = clean(value);
        if (provider == null) {
            return null;
        }
        if ("builderid".equalsIgnoreCase(provider) || "builder_id".equalsIgnoreCase(provider)) {
            return "BuilderId";
        }
        return provider;
    }

    private String firstText(JsonNode primary, JsonNode secondary, String... names) {
        for (String name : names) {
            String value = text(primary, name);
            if (value != null) {
                return value;
            }
        }
        if (secondary != null && secondary.isObject()) {
            for (String name : names) {
                String value = text(secondary, name);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String text(JsonNode node, String name) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.isTextual() ? clean(value.asText()) : clean(value.asText(null));
    }

    private Long firstLong(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            if (node.canConvertToLong()) {
                return node.asLong();
            }
            if (node.isTextual()) {
                try {
                    return Long.parseLong(node.asText().trim());
                } catch (NumberFormatException ignored) {
                    // Ignore malformed optional quota fields.
                }
            }
        }
        return null;
    }

    private Boolean booleanValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isTextual()) {
            return Boolean.parseBoolean(node.asText());
        }
        return null;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_IMPORT", message);
    }
}
