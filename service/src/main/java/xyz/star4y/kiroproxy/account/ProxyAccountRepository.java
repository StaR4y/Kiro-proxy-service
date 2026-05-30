package xyz.star4y.kiroproxy.account;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProxyAccountRepository extends ReactiveCrudRepository<ProxyAccountEntity, Long> {

    Mono<ProxyAccountEntity> findByAccountId(String accountId);

    Flux<ProxyAccountEntity> findAllByEnabledTrue();

    Mono<Void> deleteByAccountId(String accountId);
}
