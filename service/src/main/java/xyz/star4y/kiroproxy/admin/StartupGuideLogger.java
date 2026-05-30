package xyz.star4y.kiroproxy.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import xyz.star4y.kiroproxy.config.ProxyProperties;

@Component
public class StartupGuideLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupGuideLogger.class);

    private final AdminUserService adminUserService;
    private final ProxyProperties properties;
    private final Environment environment;

    public StartupGuideLogger(AdminUserService adminUserService, ProxyProperties properties, Environment environment) {
        this.adminUserService = adminUserService;
        this.properties = properties;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        adminUserService.ensureDefaultAdmin()
            .subscribe(this::logGuide, error -> log.warn("Failed to initialize default admin user", error));
    }

    private void logGuide(AdminUserService.BootstrapAdmin bootstrap) {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        String baseUrl = "http://127.0.0.1:" + port;

        log.info("------------------------------------------------------------");
        log.info("Kiro Proxy Service is ready");
        log.info("API docs:      {}/docs", baseUrl);
        log.info("Health check:  {}/health", baseUrl);
        log.info("Models API:    {}/v1/models", baseUrl);
        log.info("Login API:     POST {}/auth/login", baseUrl);
        log.info("Admin users:   GET {}/admin/users", baseUrl);
        log.info("Kiro accounts: GET {}/admin/accounts", baseUrl);

        if (bootstrap.created()) {
            log.warn("Default admin user created. Save this password now.");
            log.warn("Username: {}", bootstrap.username());
            log.warn("Password: {}", bootstrap.password());
            log.warn("Change it with POST {}/auth/password after login.", baseUrl);
        } else {
            log.info("Admin user table already initialized. Login with an existing admin user.");
        }

        if (properties.getAdminToken() != null && !properties.getAdminToken().isBlank()) {
            log.info("Legacy X-Admin-Token authentication is enabled from KIRO_ADMIN_TOKEN.");
        }

        log.info("Quick login:");
        log.info("curl -X POST {}/auth/login -H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"<password>\"}}'", baseUrl);
        log.info("Use response data.accessToken as: Authorization: Bearer <token>");
        log.info("------------------------------------------------------------");
    }
}
