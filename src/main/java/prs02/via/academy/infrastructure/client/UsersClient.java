package prs02.via.academy.infrastructure.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.infrastructure.client.dto.UserClientResponse;
import prs02.via.academy.infrastructure.utils.TenantContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class UsersClient {

    private final WebClient usersWebClient;

    public UsersClient(WebClient usersWebClient) {
        this.usersWebClient = usersWebClient;
    }

    public Mono<UserClientResponse> getUserByPersonId(UUID personId) {
        log.debug("Buscando usuario con personId: {}", personId);
        return getAllTeachers()
                .filter(user -> user.getPersonId() != null && user.getPersonId().equals(personId))
                .next()
                .doOnSuccess(user -> {
                    if (user != null) {
                        log.info("Usuario encontrado: personId={} - {} {}",
                                personId, user.getFirstName(), user.getLastName());
                    } else {
                        log.warn("No se encontró usuario con personId: {}", personId);
                    }
                })
                .doOnError(error -> log.error("Error al obtener usuario por personId: {}", personId, error));
    }

    public Mono<UserClientResponse> getUserById(UUID id) {
        log.debug("Buscando usuario por ID: {}", id);
        return usersWebClient
                .get()
                .uri("/users/{id}", id)
                .retrieve()
                .bodyToMono(UserClientResponse.class)
                .doOnError(error -> log.error("Error al obtener usuario por ID: {}", id, error));
    }

    public Flux<UserClientResponse> getAllTeachers() {
        return TenantContext.get()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Falta contexto de tenant para consultar profesores")))
                .flatMapMany(ignored -> fetchAllTeachersFromApi());
    }

    private Flux<UserClientResponse> fetchAllTeachersFromApi() {
        log.debug("Llamando a users service: /users/teacher");
        return usersWebClient
                .get()
                .uri("/users/teacher")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UserClientResponse>>() {})
                .flatMapMany(Flux::fromIterable)
                .map(u -> { u.setRole("TEACHER"); return u; })
                .doOnNext(user -> log.debug("Usuario recibido: id={}, personId={}, nombre={} {}",
                        user.getId(), user.getPersonId(), user.getFirstName(), user.getLastName()))
                .doOnError(WebClientResponseException.class, error ->
                        log.error("Error HTTP al obtener profesores: {} - {}",
                                error.getStatusCode(), error.getResponseBodyAsString()))
                .doOnError(error -> log.error("Error al obtener lista de profesores: {}", error.getMessage()))
                .onErrorResume(error -> {
                    log.warn("No se pudo obtener la lista de profesores: {}", error.getMessage());
                    return Flux.empty();
                });
    }
}
