package prs02.via.academy.application.output;

import prs02.via.academy.domain.model.RegistroAsistencia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface AttendanceRecordRepository {

    Mono<RegistroAsistencia> save(RegistroAsistencia record);

    Mono<RegistroAsistencia> findById(UUID tenantId, UUID id);

    Flux<RegistroAsistencia> findAll(UUID tenantId);

    Flux<RegistroAsistencia> findByPersonaId(UUID tenantId, UUID personaId);

    Flux<RegistroAsistencia> findByFecha(UUID tenantId, LocalDate fecha);

    Flux<RegistroAsistencia> findByPersonaIdAndFechaBetween(UUID tenantId, UUID personaId, LocalDate from, LocalDate to);

    Flux<RegistroAsistencia> findByFechaBetween(UUID tenantId, LocalDate from, LocalDate to);

    Mono<RegistroAsistencia> findByPersonaIdAndFechaAndTurno(UUID tenantId, UUID personaId, LocalDate fecha, String turno);

    Mono<Long> countPresentesByFecha(UUID tenantId, LocalDate fecha);

    Mono<Long> countTardesByFecha(UUID tenantId, LocalDate fecha);

    Mono<Long> countFaltasByFecha(UUID tenantId, LocalDate fecha);

    Mono<Long> countJustificadosByFecha(UUID tenantId, LocalDate fecha);

    Mono<Integer> sumMinutosTardanzaByFecha(UUID tenantId, LocalDate fecha);

    Mono<Integer> sumMinutosTardanzaByPersonaIdAndMes(UUID tenantId, UUID personaId, int year, int month);

    Flux<RegistroAsistencia> findByFechaBetweenAndEstadoIn(UUID tenantId, LocalDate from, LocalDate to, String estado1, String estado2);
}
