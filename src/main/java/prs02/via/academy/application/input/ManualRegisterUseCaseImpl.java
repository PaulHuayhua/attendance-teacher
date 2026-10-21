package prs02.via.academy.application.input;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.application.output.AttendanceRecordRepository;
import prs02.via.academy.domain.model.RegistroAsistencia;
import prs02.via.academy.domain.service.AttendanceDomainService;
import prs02.via.academy.infrastructure.client.SchedulesClient;
import prs02.via.academy.infrastructure.utils.TenantContext;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManualRegisterUseCaseImpl implements ManualRegisterUseCase {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SchedulesClient schedulesClient;

    @Override
    @Transactional
    public Mono<RegistroAsistencia> register(RegistroAsistencia record) {
        return TenantContext.requireTenant(record.getIdTenant())
                .flatMap(validTenantId -> {
                    log.info("Registrando asistencia manualmente para persona: {}, fecha: {}",
                            record.getPersonaId(), record.getFecha());

                    try {
                        AttendanceDomainService.validateAttendanceRecord(record);
                    } catch (Exception e) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
                    }

                    return attendanceRecordRepository
                            .findByPersonaIdAndFechaAndTurno(validTenantId, record.getPersonaId(), record.getFecha(), record.getTurno())
                            .flatMap(existing -> Mono.<RegistroAsistencia>error(new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "Ya existe un registro de asistencia para este profesor, fecha y turno")))
                            .switchIfEmpty(Mono.defer(() -> calculateAndSave(record)));
                });
    }

    private Mono<RegistroAsistencia> calculateAndSave(RegistroAsistencia record) {
        UUID personaId = record.getPersonaId();
        LocalDate fecha = record.getFecha();

        if (record.getHoraEntradaReal() != null && record.getHoraEntradaEsperada() != null) {
            long diff = Duration.between(record.getHoraEntradaEsperada(), record.getHoraEntradaReal()).toMinutes();
            record.setMinutosTardanza((int) Math.max(0, diff));
        } else {
            record.setMinutosTardanza(0);
        }

        Mono<RegistroAsistencia> setMinutos = schedulesClient.getActiveScheduleDetails(personaId, fecha)
                .filter(d -> record.getTurno() == null
                        || d.getTurno() == null
                        || d.getTurno().equalsIgnoreCase(record.getTurno()))
                .next()
                .map(detail -> {
                    if (record.getHoraEntradaReal() != null && detail.getHoraSalida() != null) {
                        long worked = Duration.between(record.getHoraEntradaReal(), detail.getHoraSalida()).toMinutes();
                        record.setMinutosTrabajados((int) Math.max(0, worked));
                    } else {
                        record.setMinutosTrabajados(0);
                    }
                    return record;
                })
                .switchIfEmpty(Mono.fromCallable(() -> {
                    record.setMinutosTrabajados(0);
                    return record;
                }));

        return setMinutos
                .doOnNext(r -> r.setCalculadoEn(LocalDateTime.now()))
                .flatMap(attendanceRecordRepository::save)
                .doOnSuccess(saved -> log.info("Asistencia manual registrada con ID: {}, tardanza: {}min, trabajados: {}min",
                        saved.getId(), saved.getMinutosTardanza(), saved.getMinutosTrabajados()));
    }
}
