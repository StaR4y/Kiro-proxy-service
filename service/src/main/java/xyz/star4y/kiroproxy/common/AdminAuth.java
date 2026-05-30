package xyz.star4y.kiroproxy.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import xyz.star4y.kiroproxy.admin.AdminPrincipal;
import xyz.star4y.kiroproxy.admin.AdminSessionService;
import xyz.star4y.kiroproxy.config.ProxyProperties;

@Component
public class AdminAuth {

    private final ProxyProperties properties;
    private final AdminSessionService sessionService;

    public AdminAuth(ProxyProperties properties, AdminSessionService sessionService) {
        this.properties = properties;
        this.sessionService = sessionService;
    }

    public AdminPrincipal verify(ServerWebExchange exchange) {
        String configured = properties.getAdminToken();
        String provided = exchange.getRequest().getHeaders().getFirst("X-Admin-Token");
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (provided == null && authorization != null && authorization.startsWith("Bearer ")) {
            provided = authorization.substring("Bearer ".length());
        }

        if (provided != null && !provided.isBlank()) {
            if (configured != null && !configured.isBlank() && configured.equals(provided)) {
                return new AdminPrincipal("env-admin", "env-admin", "ADMIN", null);
            }
            return sessionService.verify(provided);
        }

        throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "Missing admin credentials");
    }
}
