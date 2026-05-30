package xyz.star4y.kiroproxy.proxy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import xyz.star4y.kiroproxy.account.ProxyAccountEntity;
import xyz.star4y.kiroproxy.config.ProxyProperties;

@Component
public class KiroUpstreamClient {

    private static final String KIRO_VERSION = "0.12.155";
    private static final String AWS_SDK_VERSION = "1.0.34";
    private static final String AWS_STREAMING_API_VERSION = "1.0.34";

    private final WebClient webClient;
    private final ProxyProperties properties;
    private final AwsEventStreamParser parser;

    public KiroUpstreamClient(WebClient kiroWebClient, ProxyProperties properties, AwsEventStreamParser parser) {
        this.webClient = kiroWebClient;
        this.properties = properties;
        this.parser = parser;
    }

    public Mono<KiroCompletionResult> complete(ProxyAccountEntity account, ObjectNode payload, String model) {
        byte[] requestBytes = serialize(payload);
        return Mono.defer(() -> callFirstEndpoint(account, requestBytes, model))
            .timeout(properties.getRequestTimeout())
            .retryWhen(Retry.backoff(properties.getMaxRetries(), Duration.ofMillis(250))
                .maxBackoff(Duration.ofSeconds(2))
                .filter(error -> error instanceof UpstreamException upstream && upstream.isRecoverable()));
    }

    private Mono<KiroCompletionResult> callFirstEndpoint(ProxyAccountEntity account, byte[] requestBytes, String model) {
        List<ProxyProperties.Endpoint> endpoints = sortedEndpoints();
        if (endpoints.isEmpty()) {
            return Mono.error(new UpstreamException(503, "No upstream endpoints configured", true));
        }
        return callEndpoint(account, requestBytes, model, endpoints, 0);
    }

    private Mono<KiroCompletionResult> callEndpoint(
        ProxyAccountEntity account,
        byte[] requestBytes,
        String model,
        List<ProxyProperties.Endpoint> endpoints,
        int index
    ) {
        ProxyProperties.Endpoint endpoint = endpoints.get(index);
        return webClient.post()
            .uri(endpoint.getUrl())
            .headers(headers -> applyHeaders(headers, account))
            .bodyValue(requestBytes)
            .exchangeToMono(response -> handleResponse(response, requestBytes, model))
            .onErrorResume(error -> {
                if (index + 1 < endpoints.size() && isRecoverable(error)) {
                    return callEndpoint(account, requestBytes, model, endpoints, index + 1);
                }
                return Mono.error(error);
            });
    }

    private Mono<KiroCompletionResult> handleResponse(ClientResponse response, byte[] requestBytes, String model) {
        HttpStatusCode status = response.statusCode();
        if (status.isError()) {
            return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> Mono.error(new UpstreamException(
                    status.value(),
                    "Kiro upstream error " + status.value() + ": " + body,
                    isRecoverableStatus(status.value())
                )));
        }
        return response.bodyToMono(byte[].class)
            .map(bytes -> parser.parse(bytes, model, requestBytes));
    }

    private void applyHeaders(HttpHeaders headers, ProxyAccountEntity account) {
        String machineId = machineId(account);
        boolean idc = "idc".equalsIgnoreCase(account.getAuthMethod());
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + account.getAccessToken());
        headers.set("x-amzn-kiro-agent-mode", idc ? "vibe" : "spec");
        headers.set("user-agent", idc ? cliUserAgent() : kiroUserAgent(machineId));
        headers.set("x-amz-user-agent", idc ? cliAmzUserAgent() : kiroAmzUserAgent(machineId));
        headers.set("amz-sdk-invocation-id", UUID.randomUUID().toString());
        headers.set("amz-sdk-request", "attempt=1; max=3");
    }

    private List<ProxyProperties.Endpoint> sortedEndpoints() {
        String preferred = properties.getUpstream().getPreferredEndpoint();
        return properties.getUpstream().getEndpoints().stream()
            .sorted(Comparator.comparing(endpoint -> preferred != null && preferred.equals(endpoint.getName()) ? 0 : 1))
            .toList();
    }

    private boolean isRecoverable(Throwable error) {
        return error instanceof UpstreamException upstream && upstream.isRecoverable();
    }

    private boolean isRecoverableStatus(int status) {
        return status == 402 || status == 403 || status == 429;
    }

    private byte[] serialize(ObjectNode payload) {
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String machineId(ProxyAccountEntity account) {
        if (account.getMachineId() != null && !account.getMachineId().isBlank()) {
            return account.getMachineId();
        }
        return xyz.star4y.kiroproxy.common.Hashing.sha256("kiro-device-" + account.getAccountId());
    }

    private String kiroUserAgent(String machineId) {
        String os = System.getProperty("os.name", "linux").toLowerCase().contains("mac") ? "macos" : "linux";
        String release = System.getProperty("os.version", "0.0.0");
        String javaVersion = System.getProperty("java.version", "17");
        return "aws-sdk-js/%s ua/2.1 os/%s#%s lang/js md/nodejs#%s api/codewhispererstreaming#%s m/E KiroIDE-%s-%s"
            .formatted(AWS_SDK_VERSION, os, release, javaVersion, AWS_STREAMING_API_VERSION, KIRO_VERSION, machineId);
    }

    private String kiroAmzUserAgent(String machineId) {
        return "aws-sdk-js/%s KiroIDE %s %s".formatted(AWS_SDK_VERSION, KIRO_VERSION, machineId);
    }

    private String cliUserAgent() {
        return "aws-sdk-rust/1.3.9 os/macos lang/rust/1.87.0";
    }

    private String cliAmzUserAgent() {
        return "aws-sdk-rust/1.3.9 ua/2.1 api/ssooidc/1.88.0 os/macos lang/rust/1.87.0 m/E app/AmazonQ-For-CLI";
    }
}
