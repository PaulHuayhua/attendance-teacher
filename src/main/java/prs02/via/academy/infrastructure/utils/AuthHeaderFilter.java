package prs02.via.academy.infrastructure.utils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class AuthHeaderFilter implements WebFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String TENANT_CLAIM = "id_tenant";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isPublicPath(exchange)) {
            return chain.filter(exchange);
        }

        UUID tenantHeader = parseTenantHeader(exchange);
        return resolveTenant(tenantHeader)
                .flatMap(tenantId -> chain.filter(exchange)
                        .contextWrite(context -> {
                            String authorization = exchange.getRequest().getHeaders()
                                    .getFirst(HttpHeaders.AUTHORIZATION);
                            if (authorization != null) {
                                context = context.put(HttpHeaders.AUTHORIZATION, authorization);
                            }
                            return context.put(TenantContext.KEY, tenantId);
                        }));
    }

    private Mono<UUID> resolveTenant(UUID tenantHeader) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(token -> resolveClaim(token.getToken().getClaim(TENANT_CLAIM), tenantHeader))
                .switchIfEmpty(Mono.error(forbidden("El token no contiene un tenant válido")));
    }

    private UUID resolveClaim(Object claim, UUID tenantHeader) {
        Set<UUID> allowedTenants = parseAllowedTenants(claim);
        if (allowedTenants.isEmpty()) {
            throw forbidden("El token no contiene un tenant válido");
        }
        if (tenantHeader != null && !allowedTenants.contains(tenantHeader)) {
            throw forbidden("El tenant seleccionado no pertenece al token");
        }
        if (allowedTenants.size() == 1) {
            return tenantHeader != null ? tenantHeader : allowedTenants.iterator().next();
        }
        if (tenantHeader == null) {
            throw forbidden("El token contiene varios tenants; se debe seleccionar uno");
        }
        return tenantHeader;
    }

    private Set<UUID> parseAllowedTenants(Object claim) {
        if (claim instanceof String value) {
            return parseTenant(value);
        }
        if (claim instanceof Collection<?> values) {
            Set<UUID> tenants = new LinkedHashSet<>();
            for (Object value : values) {
                if (!(value instanceof String text)) {
                    return Set.of();
                }
                tenants.addAll(parseTenant(text));
            }
            return tenants;
        }
        return Set.of();
    }

    private Set<UUID> parseTenant(String value) {
        try {
            return Set.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Set.of();
        }
    }

    private UUID parseTenantHeader(ServerWebExchange exchange) {
        String value = exchange.getRequest().getHeaders().getFirst(TENANT_HEADER);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id no es un UUID válido");
        }
    }

    private boolean isPublicPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return path.equals("/actuator")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                // Endpoints ADMS: el dispositivo físico ZKTeco no envía JWT ni tenant header
                || path.startsWith("/api/v1/iclock");
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
