package prs02.via.academy.infrastructure.adapters.output;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.application.output.BiometricMarkRepository;
import prs02.via.academy.domain.model.MarcacionBiometrica;
import prs02.via.academy.infrastructure.utils.TenantContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BiometricMarkRepositoryAdapter implements BiometricMarkRepository {

    private final SpringMarcacionBiometricaRepository springRepository;

    @Override
    @Transactional
    public Mono<MarcacionBiometrica> save(UUID tenantId, MarcacionBiometrica mark) {
        return TenantContext.get()
                .flatMap(currentTenantId -> {
                    if (!currentTenantId.equals(tenantId) || !currentTenantId.equals(mark.getIdTenant())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "La marcación no pertenece al tenant actual"));
                    }
                    return springRepository.save(AttendanceEntityMapper.toEntity(mark))
                            .map(AttendanceEntityMapper::toDomain);
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Falta contexto de tenant para guardar la marcación")));
    }

    @Override
    public Flux<MarcacionBiometrica> findByPersonaIdAndFecha(UUID tenantId, UUID personaId, LocalDate fecha) {
        return TenantContext.requireTenant(tenantId)
                .flatMapMany(validTenantId -> springRepository
                        .findByTenantAndPersonaIdAndFecha(validTenantId, personaId, fecha))
                .map(AttendanceEntityMapper::toDomain);
    }

    @Override
    public Flux<MarcacionBiometrica> findNoProcesadas(UUID tenantId) {
        return TenantContext.requireTenant(tenantId)
                .flatMapMany(validTenantId -> springRepository.findByTenantAndProcesadoFalse(validTenantId))
                .map(AttendanceEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public Mono<Void> marcarComoProcesado(UUID tenantId, UUID id) {
        return TenantContext.requireTenant(tenantId)
                .flatMap(validTenantId -> springRepository.marcarComoProcesadoPorTenantAndId(validTenantId, id))
                .then();
    }

    @Override
    public Mono<MarcacionBiometrica> findById(UUID tenantId, UUID id) {
        return TenantContext.requireTenant(tenantId)
                .flatMap(validTenantId -> springRepository.findByTenantAndId(validTenantId, id))
                .map(AttendanceEntityMapper::toDomain);
    }
}
