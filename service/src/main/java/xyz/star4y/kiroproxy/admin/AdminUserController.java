package xyz.star4y.kiroproxy.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import xyz.star4y.kiroproxy.common.AdminAuth;
import xyz.star4y.kiroproxy.common.ApiResponse;

@RestController
public class AdminUserController implements AdminUserApi {

    private final AdminUserService adminUserService;
    private final AdminAuth adminAuth;

    public AdminUserController(AdminUserService adminUserService, AdminAuth adminAuth) {
        this.adminUserService = adminUserService;
        this.adminAuth = adminAuth;
    }

    @Override
    public Mono<ApiResponse<List<AdminDtos.AdminUserResponse>>> list(ServerWebExchange exchange) {
        adminAuth.verify(exchange);
        return adminUserService.list()
            .map(AdminUserMapper::toResponse)
            .collectList()
            .map(ApiResponse::ok);
    }

    @Override
    public Mono<ApiResponse<AdminDtos.AdminUserCreatedResponse>> create(
        ServerWebExchange exchange,
        @Valid @RequestBody AdminDtos.CreateAdminUserRequest request
    ) {
        adminAuth.verify(exchange);
        return adminUserService.create(request).map(ApiResponse::ok);
    }

    @Override
    public Mono<ApiResponse<AdminDtos.AdminUserResponse>> update(
        ServerWebExchange exchange,
        @PathVariable String userId,
        @RequestBody AdminDtos.UpdateAdminUserRequest request
    ) {
        adminAuth.verify(exchange);
        return adminUserService.update(userId, request)
            .map(AdminUserMapper::toResponse)
            .map(ApiResponse::ok);
    }

    @Override
    public Mono<ApiResponse<AdminDtos.ResetPasswordResponse>> resetPassword(
        ServerWebExchange exchange,
        @PathVariable String userId
    ) {
        adminAuth.verify(exchange);
        return adminUserService.resetPassword(userId).map(ApiResponse::ok);
    }
}
