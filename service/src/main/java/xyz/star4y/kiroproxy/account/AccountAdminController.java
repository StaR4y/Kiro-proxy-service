package xyz.star4y.kiroproxy.account;

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
public class AccountAdminController implements AccountAdminApi {

    private final AccountPoolService service;
    private final AdminAuth adminAuth;

    public AccountAdminController(AccountPoolService service, AdminAuth adminAuth) {
        this.service = service;
        this.adminAuth = adminAuth;
    }

    @Override
    public Mono<ApiResponse<List<AccountDtos.AccountResponse>>> list(ServerWebExchange exchange) {
        adminAuth.verify(exchange);
        return service.list()
            .map(accounts -> accounts.stream().map(AccountMapper::toResponse).toList())
            .map(ApiResponse::ok);
    }

    @Override
    public Mono<ApiResponse<AccountDtos.AccountResponse>> create(
        ServerWebExchange exchange,
        @Valid @RequestBody AccountDtos.CreateAccountRequest request
    ) {
        adminAuth.verify(exchange);
        return service.create(request)
            .map(AccountMapper::toResponse)
            .map(ApiResponse::ok);
    }

    @Override
    public Mono<ApiResponse<AccountDtos.ImportAccountsResponse>> importBatch(
        ServerWebExchange exchange,
        @Valid @RequestBody AccountDtos.ImportAccountsRequest request
    ) {
        adminAuth.verify(exchange);
        return service.importAccounts(request).map(ApiResponse::ok);
    }

    @Override
    public Mono<ApiResponse<AccountDtos.AccountResponse>> update(
        ServerWebExchange exchange,
        @PathVariable String accountId,
        @RequestBody AccountDtos.UpdateAccountRequest request
    ) {
        adminAuth.verify(exchange);
        return service.update(accountId, request)
            .map(AccountMapper::toResponse)
            .map(ApiResponse::ok);
    }

    @Override
    public Mono<ApiResponse<AccountDtos.AccountResponse>> suspend(
        ServerWebExchange exchange,
        @PathVariable String accountId,
        @Valid @RequestBody AccountDtos.SuspendAccountRequest request
    ) {
        adminAuth.verify(exchange);
        return service.suspend(accountId, request.reason(), request.message())
            .map(AccountMapper::toResponse)
            .map(ApiResponse::ok);
    }

    @Override
    public Mono<ApiResponse<AccountDtos.AccountResponse>> reset(ServerWebExchange exchange, @PathVariable String accountId) {
        adminAuth.verify(exchange);
        return service.resetState(accountId)
            .map(AccountMapper::toResponse)
            .map(ApiResponse::ok);
    }

    @Override
    public Mono<ApiResponse<Void>> delete(ServerWebExchange exchange, @PathVariable String accountId) {
        adminAuth.verify(exchange);
        return service.delete(accountId).thenReturn(ApiResponse.ok(null));
    }
}
