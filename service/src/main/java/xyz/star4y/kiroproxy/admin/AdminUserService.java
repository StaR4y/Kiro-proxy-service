package xyz.star4y.kiroproxy.admin;

import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.star4y.kiroproxy.common.ApiException;
import xyz.star4y.kiroproxy.common.Hashing;

@Service
public class AdminUserService {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_ROLE = "ADMIN";

    private final AdminUserRepository repository;
    private final PasswordHasher passwordHasher;
    private final AdminSessionService sessionService;

    public AdminUserService(
        AdminUserRepository repository,
        PasswordHasher passwordHasher,
        AdminSessionService sessionService
    ) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.sessionService = sessionService;
    }

    public Mono<BootstrapAdmin> ensureDefaultAdmin() {
        return repository.count()
            .flatMap(count -> {
                if (count > 0) {
                    return Mono.just(BootstrapAdmin.existing());
                }
                String password = passwordHasher.randomPassword();
                AdminUserEntity entity = new AdminUserEntity();
                entity.setUserId(Hashing.shortUuid());
                entity.setUsername(DEFAULT_ADMIN_USERNAME);
                entity.setDisplayName("Default Administrator");
                entity.setRole(DEFAULT_ADMIN_ROLE);
                entity.setEnabled(true);
                entity.setFirstLogin(true);
                applyPassword(entity, password);
                return repository.save(entity)
                    .map(saved -> BootstrapAdmin.created(saved.getUsername(), password))
                    .onErrorResume(DuplicateKeyException.class, error -> Mono.just(BootstrapAdmin.existing()));
            });
    }

    public Flux<AdminUserEntity> list() {
        return repository.findAll();
    }

    public Mono<AdminDtos.AdminUserCreatedResponse> create(AdminDtos.CreateAdminUserRequest request) {
        String password = request.password() == null || request.password().isBlank()
            ? passwordHasher.randomPassword()
            : request.password();
        AdminUserEntity entity = new AdminUserEntity();
        entity.setUserId(Hashing.shortUuid());
        entity.setUsername(request.username());
        entity.setDisplayName(request.displayName());
        entity.setRole(normalizeRole(request.role()));
        entity.setEnabled(true);
        entity.setFirstLogin(true);
        applyPassword(entity, password);
        return repository.save(entity)
            .map(saved -> new AdminDtos.AdminUserCreatedResponse(AdminUserMapper.toResponse(saved), password))
            .onErrorMap(
                DuplicateKeyException.class,
                error -> new ApiException(HttpStatus.CONFLICT, "ADMIN_USER_EXISTS", "Admin username already exists")
            );
    }

    public Mono<AdminUserEntity> update(String userId, AdminDtos.UpdateAdminUserRequest request) {
        return repository.findByUserId(userId)
            .switchIfEmpty(Mono.error(new ApiException(HttpStatus.NOT_FOUND, "ADMIN_USER_NOT_FOUND", "Admin user not found")))
            .flatMap(entity -> {
                if (request.displayName() != null) entity.setDisplayName(request.displayName());
                if (request.role() != null) entity.setRole(normalizeRole(request.role()));
                if (request.enabled() != null) entity.setEnabled(request.enabled());
                return repository.save(entity);
            });
    }

    public Mono<AdminDtos.ResetPasswordResponse> resetPassword(String userId) {
        return repository.findByUserId(userId)
            .switchIfEmpty(Mono.error(new ApiException(HttpStatus.NOT_FOUND, "ADMIN_USER_NOT_FOUND", "Admin user not found")))
            .flatMap(entity -> {
                String password = passwordHasher.randomPassword();
                applyPassword(entity, password);
                entity.setFirstLogin(true);
                return repository.save(entity)
                    .map(saved -> new AdminDtos.ResetPasswordResponse(saved.getUserId(), saved.getUsername(), password));
            });
    }

    public Mono<AdminDtos.LoginResponse> login(AdminDtos.LoginRequest request) {
        return repository.findByUsername(request.username())
            .switchIfEmpty(Mono.error(new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "Invalid username or password")))
            .flatMap(entity -> {
                if (Boolean.FALSE.equals(entity.getEnabled())
                    || !passwordHasher.matches(request.password(), entity.getPasswordSalt(), entity.getPasswordHash())) {
                    return Mono.error(new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "Invalid username or password"));
                }
                entity.setLastLoginAt(Instant.now());
                return repository.save(entity).map(saved -> {
                    AdminSessionService.SessionToken token = sessionService.create(saved);
                    return new AdminDtos.LoginResponse(
                        "Bearer",
                        token.token(),
                        token.expiresAt(),
                        AdminUserMapper.toResponse(saved)
                    );
                });
            });
    }

    public Mono<Void> changePassword(AdminPrincipal principal, AdminDtos.ChangePasswordRequest request) {
        return repository.findByUserId(principal.userId())
            .switchIfEmpty(Mono.error(new ApiException(HttpStatus.NOT_FOUND, "ADMIN_USER_NOT_FOUND", "Admin user not found")))
            .flatMap(entity -> {
                if (!passwordHasher.matches(request.oldPassword(), entity.getPasswordSalt(), entity.getPasswordHash())) {
                    return Mono.error(new ApiException(HttpStatus.BAD_REQUEST, "OLD_PASSWORD_INVALID", "Old password is invalid"));
                }
                applyPassword(entity, request.newPassword());
                entity.setFirstLogin(false);
                return repository.save(entity).then();
            });
    }

    private void applyPassword(AdminUserEntity entity, String password) {
        String salt = passwordHasher.newSalt();
        entity.setPasswordSalt(salt);
        entity.setPasswordHash(passwordHasher.hash(password, salt));
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return DEFAULT_ADMIN_ROLE;
        }
        return role.trim().toUpperCase();
    }

    public record BootstrapAdmin(boolean created, String username, String password) {
        static BootstrapAdmin created(String username, String password) {
            return new BootstrapAdmin(true, username, password);
        }

        static BootstrapAdmin existing() {
            return new BootstrapAdmin(false, null, null);
        }
    }
}
