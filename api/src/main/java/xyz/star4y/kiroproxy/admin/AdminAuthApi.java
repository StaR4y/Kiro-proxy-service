package xyz.star4y.kiroproxy.admin;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import xyz.star4y.kiroproxy.common.ApiResponse;

@RequestMapping("/auth")
public interface AdminAuthApi {

    @PostMapping("/login")
    Mono<ApiResponse<AdminDtos.LoginResponse>> login(@Valid @RequestBody AdminDtos.LoginRequest request);

    @PostMapping("/password")
    Mono<ApiResponse<Void>> changePassword(
        ServerWebExchange exchange,
        @Valid @RequestBody AdminDtos.ChangePasswordRequest request
    );
}
