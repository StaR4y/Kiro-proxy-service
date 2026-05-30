package xyz.star4y.kiroproxy.admin;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AdminUserRepository extends ReactiveCrudRepository<AdminUserEntity, Long> {

    Mono<AdminUserEntity> findByUsername(String username);

    Mono<AdminUserEntity> findByUserId(String userId);
}
