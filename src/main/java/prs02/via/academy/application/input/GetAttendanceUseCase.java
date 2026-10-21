package prs02.via.academy.application.input;

import prs02.via.academy.domain.model.RegistroAsistencia;
import prs02.via.academy.domain.model.TardinessReport;
import prs02.via.academy.domain.model.TeacherRecoveryAlert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface GetAttendanceUseCase {
    Flux<RegistroAsistencia> findAll(UUID tenantId);
    Flux<RegistroAsistencia> findByPersonaId(UUID tenantId, UUID personaId);
    Flux<RegistroAsistencia> findByFecha(UUID tenantId, LocalDate fecha);
    Flux<RegistroAsistencia> findByFechaBetween(UUID tenantId, LocalDate from, LocalDate to);
    Flux<RegistroAsistencia> findHistoryByPersonaId(UUID tenantId, UUID personaId, LocalDate from, LocalDate to);
    Mono<RegistroAsistencia> findById(UUID tenantId, UUID id);
    Mono<Map<String, Object>> getSummary(UUID tenantId, LocalDate fecha);
    Flux<TardinessReport> getTardinessReport(UUID tenantId, int year, int month);
    Mono<TardinessReport> getTeacherTardinessReport(UUID tenantId, UUID personaId, int year, int month);
    Flux<TeacherRecoveryAlert> getRecoveryAlerts(UUID tenantId, LocalDate from, LocalDate to);
    Mono<RegistroAsistencia> save(RegistroAsistencia record);
}
