package xyz.star4y.kiroproxy.admin;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
    ) {
    }

    public record LoginResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        AdminUserResponse user
    ) {
    }

    public record ChangePasswordRequest(
        @NotBlank String oldPassword,
        @NotBlank String newPassword
    ) {
    }

    public record CreateAdminUserRequest(
        @NotBlank String username,
        String displayName,
        String role,
        String password
    ) {
    }

    public record UpdateAdminUserRequest(
        String displayName,
        String role,
        Boolean enabled
    ) {
    }

    public record AdminUserCreatedResponse(
        AdminUserResponse user,
        String password
    ) {
    }

    public record ResetPasswordResponse(
        String userId,
        String username,
        String password
    ) {
    }

    public record AdminUserResponse(
        String userId,
        String username,
        String displayName,
        String role,
        Boolean enabled,
        Boolean firstLogin,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
