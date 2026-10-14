package prs02.via.academy.infrastructure.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Configuration
public class WebClientConfig {

    @Value("${services.schedules.url:http://localhost:8083/api/v1}")
    private String schedulesServiceUrl;

    @Value("${services.users.url:https://lab.vallegrande.edu.pe/prs02/via-academy/gateway/user/api/v1}")
    private String usersServiceUrl;

    @Value("${services.justifications.url:https://lab.vallegrande.edu.pe/prs02/via-academy/gateway/justifications/v0.1/api}")
    private String justificationsServiceUrl;

    @Bean
    public WebClient schedulesWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(schedulesServiceUrl)
                .filter(authFilter())
                .build();
    }

    @Bean
    public WebClient usersWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(usersServiceUrl)
                .filter(authFilter())
                .build();
    }

    @Bean
    public WebClient justificationsWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(justificationsServiceUrl)
                .filter(authFilter())
                .build();
    }

    private ExchangeFilterFunction authFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request ->
                Mono.deferContextual(ctx -> TenantContext.get()
                        .flatMap(tenantId -> {
                            if (!ctx.hasKey(HttpHeaders.AUTHORIZATION)) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED, "Falta el token de autorización para la llamada interna"));
                            }
                            String token = ctx.get(HttpHeaders.AUTHORIZATION);
                            return Mono.just(ClientRequest.from(request)
                                    .headers(headers -> {
                                        headers.set(HttpHeaders.AUTHORIZATION, token);
                                        headers.set("X-Tenant-Id", tenantId.toString());
                                    })
                                    .build());
                        })
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Falta contexto de tenant para la llamada interna"))))
        );
    }
}
