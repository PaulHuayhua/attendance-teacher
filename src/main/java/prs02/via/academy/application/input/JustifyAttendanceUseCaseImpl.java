package prs02.via.academy.application.input;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.application.output.AttendanceRecordRepository;
import prs02.via.academy.application.output.JustificationPort;
import prs02.via.academy.domain.model.RegistroAsistencia;
import prs02.via.academy.domain.service.AttendanceDomainService;
import prs02.via.academy.infrastructure.utils.TenantContext;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JustifyAttendanceUseCaseImpl implements JustifyAttendanceUseCase {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final JustificationPort justificationPort;

    @Override
    @Transactional
    public Mono<RegistroAsistencia> justify(UUID tenantId, UUID registroId, String motivo, String usuario) {
        return TenantContext.requireTenant(tenantId)
                .flatMap(validTenantId -> {
                    log.info("Justificando asistencia ID: {}, motivo: {}, usuario: {}", registroId, motivo, usuario);

                    return attendanceRecordRepository.findById(validTenantId, registroId)
                            .switchIfEmpty(Mono.error(new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Registro de asistencia no encontrado: " + registroId)))
                            .flatMap(record -> {
                                try {
                                    AttendanceDomainService.validateJustification(record, motivo, usuario);
                                } catch (Exception e) {
                                    return Mono.<RegistroAsistencia>error(
                                            new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
                                }

                                RegistroAsistencia justified = AttendanceDomainService.justifyAttendance(record, motivo, usuario);

                                return attendanceRecordRepository.save(justified)
                                        .flatMap(savedRecord -> {
                                            log.info("Enviando justificación al microservicio externo...");
                                            return justificationPort.sendJustification(savedRecord, motivo, usuario)
                                                    .doOnSuccess(justificationId ->
                                                            log.info("Justificación enviada exitosamente. ID externo: {}", justificationId))
                                                    .doOnError(error ->
                                                            log.warn("No se pudo enviar al microservicio de justificaciones: {}", error.getMessage()))
                                                    .then(Mono.just(savedRecord))
                                                    .onErrorReturn(savedRecord);
                                        });
                            });
                })
                .doOnSuccess(r -> log.info("Asistencia {} justificada exitosamente (local y externo)", registroId))
                .onErrorMap(ex -> {
                    if (ex instanceof ResponseStatusException) return ex;
                    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Error al justificar asistencia: " + ex.getMessage());
                });
    }
}
