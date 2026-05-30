package xyz.star4y.kiroproxy.admin;

import java.time.Instant;

public record AdminPrincipal(
    String userId,
    String username,
    String role,
    Instant expiresAt
) {
}
