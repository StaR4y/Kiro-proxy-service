package xyz.star4y.kiroproxy.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface ProxyApi {

    @GetMapping({"/health", "/v1/health"})
    Mono<ResponseEntity<?>> health();

    @GetMapping({"/v1/models", "/models"})
    Mono<ResponseEntity<?>> models();

    @PostMapping({"/v1/chat/completions", "/chat/completions"})
    Mono<ResponseEntity<?>> chat(@RequestBody JsonNode request, ServerWebExchange exchange);

    @PostMapping({"/v1/responses", "/responses"})
    Mono<ResponseEntity<?>> responses(@RequestBody JsonNode request, ServerWebExchange exchange);

    @PostMapping({"/v1/messages", "/messages"})
    Mono<ResponseEntity<?>> messages(@RequestBody JsonNode request, ServerWebExchange exchange);
}
