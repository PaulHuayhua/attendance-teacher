package prs02.via.academy.application.input;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.application.output.AttendanceRecordRepository;
import prs02.via.academy.application.output.BiometricMarkRepository;
import prs02.via.academy.application.output.MarkAttendanceRepository;
import prs02.via.academy.domain.model.*;
import prs02.via.academy.domain.service.AttendanceDomainService;
import prs02.via.academy.infrastructure.client.SchedulesClient;
import prs02.via.academy.infrastructure.utils.TenantContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessAttendanceUseCaseImpl implements ProcessAttendanceUseCase {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final BiometricMarkRepository biometricMarkRepository;
    private final MarkAttendanceRepository markAttendanceRepository;
    private final SchedulesClient schedulesClient;

    @Override
    @Transactional
    public Flux<RegistroAsistencia> processAttendance(UUID tenantId, UUID personaId, LocalDate date) {
        return TenantContext.requireTenant(tenantId)
                .flatMapMany(validTenantId -> {
                    log.info("Procesando asistencia para persona: {}, fecha: {}", personaId, date);
                    return schedulesClient.getActiveScheduleDetails(personaId, date)
                            .switchIfEmpty(Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "No se encontró horario activo para la persona en la fecha especificada")))
                            .filter(HorarioDetalleDTO::getEsLaborable)
                            .flatMap(detalle -> processTurno(validTenantId, personaId, date, detalle));
                });
    }

    private Mono<RegistroAsistencia> processTurno(UUID tenantId, UUID personaId, LocalDate fecha, HorarioDetalleDTO detalle) {
        String turno = detalle.getTurno();

        return attendanceRecordRepository.findByPersonaIdAndFechaAndTurno(tenantId, personaId, fecha, turno)
                .flatMap(existing -> {
                    log.debug("Ya existe registro de asistencia para persona {} fecha {} turno {}, saltando", personaId, fecha, turno);
                    return Mono.<RegistroAsistencia>empty();
                })
                .switchIfEmpty(Mono.defer(() ->
                        biometricMarkRepository.findByPersonaIdAndFecha(tenantId, personaId, fecha)
                                .collectList()
                                .flatMap(marks -> {
                                    RegistroAsistencia record = AttendanceDomainService.calculateAttendance(
                                            personaId, detalle, fecha, marks);

                                    if (record == null) return Mono.empty();

                                    record.setIdTenant(tenantId);
return attendanceRecordRepository.save(record)
                                             .flatMap(saved -> linkMarkIfPresent(tenantId, saved, marks, detalle));
                                })
                ));
    }

    private Mono<RegistroAsistencia> linkMarkIfPresent(UUID tenantId, RegistroAsistencia record, List<MarcacionBiometrica> marks, HorarioDetalleDTO detalle) {
        if (record.getHoraEntradaReal() == null) {
            return Mono.just(record);
        }

        return marks.stream()
                .filter(m -> AttendanceDomainService.isWithinWindow(m.getFechaHora().toLocalTime(), detalle.getHoraEntrada()))
                .findFirst()
                .map(mark -> markAttendanceRepository.save(
                                MarcacionAsistencia.builder()
                                        .registroAsistenciaId(record.getId())
                                        .marcacionId(mark.getId())
                                        .rolMarcacion("ENTRADA")
                                        .build())
                        .then(biometricMarkRepository.marcarComoProcesado(tenantId, mark.getId()))
                        .thenReturn(record))
                .orElse(Mono.just(record));
    }

    @Override
    @Transactional
    public Flux<RegistroAsistencia> processAllPending() {
        log.info("Procesando todas las marcaciones pendientes...");

        return TenantContext.get()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Se requiere contexto de tenant para procesar marcaciones")))
                .flatMapMany(tenantId ->
                        biometricMarkRepository.findNoProcesadas(tenantId)
                                .collectList()
                                .flatMapMany(marks -> {
                                    if (marks.isEmpty()) {
                                        log.info("No hay marcaciones pendientes por procesar");
                                        return Flux.empty();
                                    }

                                    return Flux.fromIterable(marks)
                                            .groupBy(m -> m.getPersonaId())
                                            .flatMap(group -> {
                                                UUID personaId = group.key();
                                                return group.collectList()
                                                        .flatMapMany(personMarks -> {
                                                            LocalDate fecha = personMarks.get(0).getFechaHora().toLocalDate();
                                                            return processAttendance(tenantId, personaId, fecha);
                                                        });
                                            });
                                })
                );
    }
}
