package prs02.via.academy.infrastructure.utils;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class TenantContext {

    public static final String KEY = "TENANT_ID";

    private TenantContext() {}

    public static Mono<UUID> get() {
        return Mono.deferContextual(context -> {
            Object value = context.getOrDefault(KEY, null);
            return value instanceof UUID tenantId
                    ? Mono.just(tenantId)
                    : Mono.empty();
        });
    }

    public static Mono<UUID> requireTenant(UUID tenantId) {
        if (tenantId == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "El token no contiene un tenant válido"));
        }
        return get()
                .filter(currentTenantId -> currentTenantId.equals(tenantId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "El tenant no coincide con el contexto actual")))
                .then(Mono.just(tenantId));
    }

    public static <T> Mono<T> withTenant(Function<UUID, Mono<T>> action) {
        Objects.requireNonNull(action, "action");
        return get()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "El token no contiene un tenant válido")))
                .flatMap(action);
    }
}
