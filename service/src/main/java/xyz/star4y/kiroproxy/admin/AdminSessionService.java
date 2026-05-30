package xyz.star4y.kiroproxy.admin;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.star4y.kiroproxy.common.ApiException;
import xyz.star4y.kiroproxy.common.Hashing;
import xyz.star4y.kiroproxy.config.ProxyProperties;

@Service
public class AdminSessionService {

    private final ProxyProperties properties;
    private final Map<String, AdminPrincipal> sessions = new ConcurrentHashMap<>();

    public AdminSessionService(ProxyProperties properties) {
        this.properties = properties;
    }

    public SessionToken create(AdminUserEntity user) {
        Instant expiresAt = Instant.now().plus(properties.getAdminSessionTtl());
        String token = "adm-" + Hashing.randomToken(32);
        sessions.put(token, new AdminPrincipal(user.getUserId(), user.getUsername(), user.getRole(), expiresAt));
        return new SessionToken(token, expiresAt);
    }

    public AdminPrincipal verify(String token) {
        AdminPrincipal principal = sessions.get(token);
        if (principal == null || principal.expiresAt().isBefore(Instant.now())) {
            if (principal != null) {
                sessions.remove(token);
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "Invalid or expired admin session");
        }
        return principal;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record SessionToken(String token, Instant expiresAt) {
    }
}
