package xyz.star4y.kiroproxy.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.Exceptions;
import xyz.star4y.kiroproxy.proxy.KiroCompletionResult;
import xyz.star4y.kiroproxy.proxy.KiroPayloadFactory;
import xyz.star4y.kiroproxy.proxy.KiroUpstreamClient;
import xyz.star4y.kiroproxy.proxy.KiroUsage;
import xyz.star4y.kiroproxy.proxy.UpstreamException;

class AccountTestServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testsAccountsWithLightweightUpstreamCall() {
        ProxyAccountRepository repository = mock(ProxyAccountRepository.class);
        AccountPoolService accountPoolService = mock(AccountPoolService.class);
        KiroUpstreamClient upstreamClient = mock(KiroUpstreamClient.class);
        ProxyAccountEntity account = account("kiro-main", "token");

        when(repository.findAll()).thenReturn(Flux.just(account));
        when(upstreamClient.complete(any(ProxyAccountEntity.class), any(ObjectNode.class), eq("simple-task")))
            .thenReturn(Mono.just(new KiroCompletionResult("OK", KiroUsage.zero())));
        when(accountPoolService.recordSuccess(eq("kiro-main"), eq(0L))).thenReturn(Mono.empty());

        AccountTestService service = new AccountTestService(
            objectMapper,
            repository,
            accountPoolService,
            new KiroPayloadFactory(objectMapper),
            upstreamClient
        );

        AccountDtos.TestAccountsResponse response = service
            .test(new AccountDtos.TestAccountsRequest(null, true, "simple-task", "ping", 1))
            .block();

        assertThat(response).isNotNull();
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.success()).isEqualTo(1);
        assertThat(response.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.accountId()).isEqualTo("kiro-main");
                assertThat(item.status()).isEqualTo("SUCCESS");
                assertThat(item.statusCode()).isEqualTo(200);
            });

        ArgumentCaptor<ObjectNode> payloadCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(upstreamClient).complete(eq(account), payloadCaptor.capture(), eq("simple-task"));
        assertThat(payloadCaptor.getValue()
            .path("conversationState")
            .path("currentMessage")
            .path("userInputMessage")
            .path("origin")
            .asText()).isEqualTo("AI_EDITOR");
    }

    @Test
    void skipsAccountsWithoutAccessToken() {
        ProxyAccountRepository repository = mock(ProxyAccountRepository.class);
        AccountPoolService accountPoolService = mock(AccountPoolService.class);
        KiroUpstreamClient upstreamClient = mock(KiroUpstreamClient.class);

        when(repository.findAll()).thenReturn(Flux.just(account("empty", "")));

        AccountTestService service = new AccountTestService(
            objectMapper,
            repository,
            accountPoolService,
            new KiroPayloadFactory(objectMapper),
            upstreamClient
        );

        AccountDtos.TestAccountsResponse response = service
            .test(new AccountDtos.TestAccountsRequest(null, true, null, null, 1))
            .block();

        assertThat(response).isNotNull();
        assertThat(response.skipped()).isEqualTo(1);
        assertThat(response.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo("SKIPPED");
                assertThat(item.message()).isEqualTo("Access token is empty");
            });
        verifyNoInteractions(upstreamClient, accountPoolService);
    }

    @Test
    void reportsRootUpstreamErrorWhenRetryWrapperIsPresent() {
        ProxyAccountRepository repository = mock(ProxyAccountRepository.class);
        AccountPoolService accountPoolService = mock(AccountPoolService.class);
        KiroUpstreamClient upstreamClient = mock(KiroUpstreamClient.class);
        ProxyAccountEntity account = account("kiro-main", "token");
        UpstreamException root = new UpstreamException(429, "Kiro upstream error 429 on amazonq: quota", true);

        when(repository.findAll()).thenReturn(Flux.just(account));
        when(upstreamClient.complete(any(ProxyAccountEntity.class), any(ObjectNode.class), eq("auto")))
            .thenReturn(Mono.error(Exceptions.retryExhausted("Retries exhausted: 2/2", root)));
        when(accountPoolService.recordError(eq("kiro-main"), eq(AccountPoolService.UpstreamErrorType.RECOVERABLE), eq(429)))
            .thenReturn(Mono.empty());

        AccountTestService service = new AccountTestService(
            objectMapper,
            repository,
            accountPoolService,
            new KiroPayloadFactory(objectMapper),
            upstreamClient
        );

        AccountDtos.TestAccountsResponse response = service
            .test(new AccountDtos.TestAccountsRequest(null, true, "auto", "ping", 1))
            .block();

        assertThat(response).isNotNull();
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.statusCode()).isEqualTo(429);
                assertThat(item.detail()).isEqualTo("Kiro upstream error 429 on amazonq: quota");
            });
    }

    private ProxyAccountEntity account(String accountId, String accessToken) {
        ProxyAccountEntity account = new ProxyAccountEntity();
        account.setAccountId(accountId);
        account.setEmail(accountId + "@example.com");
        account.setAccessToken(accessToken);
        account.setEnabled(true);
        account.setQuotaUsed(0L);
        account.setQuotaLimit(1000L);
        account.setRegion("us-east-1");
        account.setAuthMethod("idc");
        return account;
    }
}
