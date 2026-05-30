package xyz.star4y.kiroproxy.apikey;

import java.math.BigDecimal;

public record ApiKeyPrincipal(
    String keyId,
    String name,
    BigDecimal creditsLimit,
    BigDecimal totalCredits
) {
}
