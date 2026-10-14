package prs02.via.academy.application.input;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.application.output.BiometricMarkRepository;
import prs02.via.academy.domain.model.MarcacionBiometrica;
import prs02.via.academy.domain.service.AttendanceDomainService;
import prs02.via.academy.infrastructure.utils.TenantContext;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiveMarkUseCaseImpl implements ReceiveMarkUseCase {

    private final BiometricMarkRepository biometricMarkRepository;

    @Override
    public Mono<MarcacionBiometrica> receiveMark(MarcacionBiometrica mark) {
        return TenantContext.get()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Falta contexto de tenant para guardar la marcación")))
                .flatMap(tenantId -> {
                    mark.setIdTenant(tenantId);
                    log.info("Recibiendo marcación biométrica para persona: {}", mark.getPersonaId());

                    try {
                        AttendanceDomainService.validateBiometricMark(mark);
                    } catch (Exception e) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
                    }

                    mark.setProcesado(false);
                    mark.setRegistradoEn(LocalDateTime.now());

                    return biometricMarkRepository.save(tenantId, mark)
                            .doOnSuccess(saved -> log.info("Marcación guardada con ID: {}", saved.getId()))
                            .onErrorMap(ex -> {
                                log.error("Error guardando marcación: {}", ex.getMessage());
                                if (ex instanceof ResponseStatusException) return ex;
                                return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                        "Error interno al guardar la marcación: " + ex.getMessage());
                            });
                });
    }
}
