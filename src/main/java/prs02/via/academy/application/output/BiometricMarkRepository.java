package prs02.via.academy.application.output;

import prs02.via.academy.domain.model.MarcacionBiometrica;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface BiometricMarkRepository {

    Mono<MarcacionBiometrica> save(UUID tenantId, MarcacionBiometrica mark);

    Flux<MarcacionBiometrica> findByPersonaIdAndFecha(UUID tenantId, UUID personaId, LocalDate fecha);

    Flux<MarcacionBiometrica> findNoProcesadas(UUID tenantId);

    Mono<Void> marcarComoProcesado(UUID tenantId, UUID id);

    Mono<MarcacionBiometrica> findById(UUID tenantId, UUID id);
}
