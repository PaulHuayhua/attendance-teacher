package prs02.via.academy.application.input;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.application.output.AttendanceRecordRepository;
import prs02.via.academy.domain.model.RegistroAsistencia;
import prs02.via.academy.domain.model.TardinessReport;
import prs02.via.academy.domain.model.TeacherRecoveryAlert;
import prs02.via.academy.infrastructure.utils.TenantContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetAttendanceUseCaseImpl implements GetAttendanceUseCase {

    private final AttendanceRecordRepository attendanceRecordRepository;

    private static final int UMBRAL_TARDANZA_MINUTOS = 60;

    @Override
    public Flux<RegistroAsistencia> findAll(UUID tenantId) {
        return TenantContext.requireTenant(tenantId)
                .flatMapMany(attendanceRecordRepository::findAll);
    }

    @Override
    public Flux<RegistroAsistencia> findByPersonaId(UUID tenantId, UUID personaId) {
        return TenantContext.requireTenant(tenantId)
                .flatMapMany(validTenantId -> attendanceRecordRepository.findByPersonaId(validTenantId, personaId));
    }

    @Override
    public Flux<RegistroAsistencia> findByFecha(UUID tenantId, LocalDate fecha) {
        return TenantContext.requireTenant(tenantId)
                .flatMapMany(validTenantId -> attendanceRecordRepository.findByFecha(validTenantId, fecha));
    }

    @Override
    public Flux<RegistroAsistencia> findByFechaBetween(UUID tenantId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio debe ser anterior a la fecha de fin"));
        }
        if (from != null && to != null && from.plusYears(1).isBefore(to)) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El rango de fechas es demasiado amplio. Máximo 12 meses"));
        }
        return TenantContext.requireTenant(tenantId)
                .flatMapMany(validTenantId -> attendanceRecordRepository.findByFechaBetween(validTenantId, from, to));
    }

    @Override
    public Flux<RegistroAsistencia> findHistoryByPersonaId(UUID tenantId, UUID personaId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio debe ser anterior a la fecha de fin"));
        }
        return TenantContext.requireTenant(tenantId)
                .flatMapMany(validTenantId -> attendanceRecordRepository.findByPersonaIdAndFechaBetween(validTenantId, personaId, from, to));
    }

    @Override
    public Mono<RegistroAsistencia> findById(UUID tenantId, UUID id) {
        return TenantContext.requireTenant(tenantId)
                .flatMap(validTenantId -> attendanceRecordRepository.findById(validTenantId, id))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Registro de asistencia no encontrado con id: " + id)));
    }

    @Override
    public Mono<Map<String, Object>> getSummary(UUID tenantId, LocalDate fecha) {
        LocalDate targetDate = fecha != null ? fecha : LocalDate.now();

        return TenantContext.requireTenant(tenantId)
                .flatMap(validTenantId -> {
                    Mono<Long> presentes = attendanceRecordRepository.countPresentesByFecha(validTenantId, targetDate);
                    Mono<Long> tardes = attendanceRecordRepository.countTardesByFecha(validTenantId, targetDate);
                    Mono<Long> faltas = attendanceRecordRepository.countFaltasByFecha(validTenantId, targetDate);
                    Mono<Long> justificados = attendanceRecordRepository.countJustificadosByFecha(validTenantId, targetDate);
                    Mono<Integer> totalMinutos = attendanceRecordRepository.sumMinutosTardanzaByFecha(validTenantId, targetDate);

                    return Mono.zip(presentes, tardes, faltas, justificados, totalMinutos)
                            .map(tuple -> {
                                Map<String, Object> summary = new HashMap<>();
                                summary.put("fecha", targetDate.toString());
                                summary.put("total", tuple.getT1() + tuple.getT2() + tuple.getT3() + tuple.getT4());
                                summary.put("presentes", tuple.getT1());
                                summary.put("tardes", tuple.getT2());
                                summary.put("faltas", tuple.getT3());
                                summary.put("justificados", tuple.getT4());
                                summary.put("totalMinutosTardanza", tuple.getT5());
                                return summary;
                            });
                });
    }

    @Override
    public Flux<TardinessReport> getTardinessReport(UUID tenantId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        String mes = String.format("%04d-%02d", year, month);

        Flux<RegistroAsistencia> records = TenantContext.requireTenant(tenantId)
                .flatMapMany(validTenantId -> attendanceRecordRepository.findByFechaBetween(validTenantId, startDate, endDate));

        return records
                .collectList()
                .flatMapMany(recordList -> {
                    Map<UUID, List<RegistroAsistencia>> grouped = recordList.stream()
                            .collect(Collectors.groupingBy(RegistroAsistencia::getPersonaId));

                    List<TardinessReport> reports = new ArrayList<>();
                    for (Map.Entry<UUID, List<RegistroAsistencia>> entry : grouped.entrySet()) {
                        List<RegistroAsistencia> teacherRecords = entry.getValue();
                        int totalTardanza = teacherRecords.stream()
                                .filter(r -> Boolean.FALSE.equals(r.getJustificado()))
                                .filter(r -> r.getMinutosTardanza() != null)
                                .mapToInt(RegistroAsistencia::getMinutosTardanza)
                                .sum();
                        long totalFaltas = teacherRecords.stream()
                                .filter(r -> "FALTA".equals(r.getEstado()))
                                .count();
                        long totalAsistencias = teacherRecords.stream()
                                .filter(r -> "PRESENTE".equals(r.getEstado()) || "TARDE".equals(r.getEstado()))
                                .count();

                        reports.add(TardinessReport.builder()
                                .personaId(entry.getKey())
                                .mes(mes)
                                .totalMinutosTardanza(totalTardanza)
                                .totalFaltas((int) totalFaltas)
                                .totalAsistencias((int) totalAsistencias)
                                .umbral(UMBRAL_TARDANZA_MINUTOS)
                                .alertaGenerada(totalTardanza > UMBRAL_TARDANZA_MINUTOS)
                                .build());
                    }
                    return Flux.fromIterable(reports);
                });
    }

    @Override
    public Mono<TardinessReport> getTeacherTardinessReport(UUID tenantId, UUID personaId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        String mes = String.format("%04d-%02d", year, month);

        Flux<RegistroAsistencia> records = TenantContext.requireTenant(tenantId)
                .flatMapMany(validTenantId -> attendanceRecordRepository.findByPersonaIdAndFechaBetween(validTenantId, personaId, startDate, endDate));

        return records
                .collectList()
                .map(recordList -> {
                    int totalTardanza = recordList.stream()
                            .filter(r -> Boolean.FALSE.equals(r.getJustificado()))
                            .filter(r -> r.getMinutosTardanza() != null)
                            .mapToInt(RegistroAsistencia::getMinutosTardanza)
                            .sum();
                    long totalFaltas = recordList.stream()
                            .filter(r -> "FALTA".equals(r.getEstado()))
                            .count();
                    long totalAsistencias = recordList.stream()
                            .filter(r -> "PRESENTE".equals(r.getEstado()) || "TARDE".equals(r.getEstado()))
                            .count();

                    return TardinessReport.builder()
                            .personaId(personaId)
                            .mes(mes)
                            .totalMinutosTardanza(totalTardanza)
                            .totalFaltas((int) totalFaltas)
                            .totalAsistencias((int) totalAsistencias)
                            .umbral(UMBRAL_TARDANZA_MINUTOS)
                            .alertaGenerada(totalTardanza > UMBRAL_TARDANZA_MINUTOS)
                            .build();
                });
    }

    @Override
    public Flux<TeacherRecoveryAlert> getRecoveryAlerts(UUID tenantId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las fechas from y to son requeridas"));
        }
        if (from.isAfter(to)) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio debe ser anterior a la fecha de fin"));
        }

        Flux<RegistroAsistencia> records = TenantContext.requireTenant(tenantId)
                .flatMapMany(validTenantId -> attendanceRecordRepository.findByFechaBetweenAndEstadoIn(
                        validTenantId, from, to, "TARDE", "FALTA"));

        return records
                .collectList()
                .flatMapMany(recordList -> {
                    Map<UUID, List<RegistroAsistencia>> grouped = recordList.stream()
                            .collect(Collectors.groupingBy(RegistroAsistencia::getPersonaId));

                    List<TeacherRecoveryAlert> alerts = new ArrayList<>();
                    for (Map.Entry<UUID, List<RegistroAsistencia>> entry : grouped.entrySet()) {
                        UUID personaId = entry.getKey();
                        List<RegistroAsistencia> teacherRecords = entry.getValue();

                        int totalTardanzas = (int) teacherRecords.stream()
                                .filter(r -> "TARDE".equals(r.getEstado()))
                                .count();
                        int totalFaltas = (int) teacherRecords.stream()
                                .filter(r -> "FALTA".equals(r.getEstado()))
                                .count();
                        int minutosAcumulados = teacherRecords.stream()
                                .filter(r -> r.getMinutosTardanza() != null)
                                .mapToInt(RegistroAsistencia::getMinutosTardanza)
                                .sum();
                        double horasRecuperar = minutosAcumulados >= 60
                                ? Math.round((minutosAcumulados / 60.0) * 10.0) / 10.0
                                : 0;

                        alerts.add(TeacherRecoveryAlert.builder()
                                .personaId(personaId)
                                .totalRegistros(teacherRecords.size())
                                .totalTardanzas(totalTardanzas)
                                .totalFaltas(totalFaltas)
                                .minutosTardanzaAcumulados(minutosAcumulados)
                                .horasARecuperar(horasRecuperar)
                                .requiereRecuperacion(minutosAcumulados >= UMBRAL_TARDANZA_MINUTOS)
                                .build());
                    }

                    alerts.sort((a, b) -> Integer.compare(
                            b.getMinutosTardanzaAcumulados(),
                            a.getMinutosTardanzaAcumulados()));

                    return Flux.fromIterable(alerts);
                });
    }

    @Override
    public Mono<RegistroAsistencia> save(RegistroAsistencia record) {
        return TenantContext.requireTenant(record.getIdTenant())
                .flatMap(validTenantId -> {
                    if (!validTenantId.equals(record.getIdTenant())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "El registro no pertenece al tenant actual"));
                    }
                    return attendanceRecordRepository.save(record);
                });
    }
}
