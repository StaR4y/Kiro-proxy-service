package xyz.star4y.kiroproxy.admin;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import xyz.star4y.kiroproxy.common.AdminAuth;
import xyz.star4y.kiroproxy.common.ApiResponse;

@RestController
public class AdminAuthController implements AdminAuthApi {

    private final AdminUserService adminUserService;
    private final AdminAuth adminAuth;

    public AdminAuthController(AdminUserService adminUserService, AdminAuth adminAuth) {
        this.adminUserService = adminUserService;
        this.adminAuth = adminAuth;
    }

    @Override
    public Mono<ApiResponse<AdminDtos.LoginResponse>> login(@Valid @RequestBody AdminDtos.LoginRequest request) {
        return adminUserService.login(request).map(ApiResponse::ok);
    }

    @Override
    public Mono<ApiResponse<Void>> changePassword(
        ServerWebExchange exchange,
        @Valid @RequestBody AdminDtos.ChangePasswordRequest request
    ) {
        AdminPrincipal principal = adminAuth.verifyAllowFirstLogin(exchange);
        return adminUserService.changePassword(principal, request).thenReturn(ApiResponse.ok(null));
    }
}
