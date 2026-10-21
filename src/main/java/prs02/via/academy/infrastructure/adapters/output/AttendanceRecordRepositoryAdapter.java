package prs02.via.academy.infrastructure.adapters.output;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.application.output.AttendanceRecordRepository;
import prs02.via.academy.domain.model.RegistroAsistencia;
import prs02.via.academy.infrastructure.utils.TenantContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AttendanceRecordRepositoryAdapter implements AttendanceRecordRepository {

    private final SpringRegistroAsistenciaRepository springRepository;

    @Override
    @Transactional
    public Mono<RegistroAsistencia> save(RegistroAsistencia record) {
        return TenantContext.get()
                .flatMap(tenantId -> {
                    if (!tenantId.equals(record.getIdTenant())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "El registro no pertenece al tenant actual"));
                    }
                    RegistroAsistenciaEntity entity = AttendanceEntityMapper.toEntity(record);
                    return springRepository.save(entity)
                            .map(AttendanceEntityMapper::toDomain);
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Falta contexto de tenant para guardar la asistencia")));
    }

    @Override
    public Mono<RegistroAsistencia> findById(UUID tenantId, UUID id) {
        return springRepository.findByTenantAndId(tenantId, id)
                .map(AttendanceEntityMapper::toDomain);
    }

    @Override
    public Flux<RegistroAsistencia> findAll(UUID tenantId) {
        return springRepository.findAllByTenant(tenantId)
                .map(AttendanceEntityMapper::toDomain);
    }

    @Override
    public Flux<RegistroAsistencia> findByPersonaId(UUID tenantId, UUID personaId) {
        return springRepository.findByTenantAndPersonaId(tenantId, personaId)
                .map(AttendanceEntityMapper::toDomain);
    }

    @Override
    public Flux<RegistroAsistencia> findByFecha(UUID tenantId, LocalDate fecha) {
        return springRepository.findByTenantAndFecha(tenantId, fecha)
                .map(AttendanceEntityMapper::toDomain);
    }

    @Override
    public Flux<RegistroAsistencia> findByPersonaIdAndFechaBetween(UUID tenantId, UUID personaId, LocalDate from, LocalDate to) {
        return springRepository.findByTenantAndPersonaIdAndFechaBetween(tenantId, personaId, from, to)
                .map(AttendanceEntityMapper::toDomain);
    }

    @Override
    public Flux<RegistroAsistencia> findByFechaBetween(UUID tenantId, LocalDate from, LocalDate to) {
        return springRepository.findByTenantAndFechaBetween(tenantId, from, to)
                .map(AttendanceEntityMapper::toDomain);
    }

    @Override
    public Mono<RegistroAsistencia> findByPersonaIdAndFechaAndTurno(UUID tenantId, UUID personaId, LocalDate fecha, String turno) {
        return springRepository.findByTenantAndPersonaIdAndFechaAndTurno(tenantId, personaId, fecha, turno)
                .map(AttendanceEntityMapper::toDomain);
    }

    @Override
    public Mono<Long> countPresentesByFecha(UUID tenantId, LocalDate fecha) {
        return springRepository.countByTenantAndFechaAndEstadoPresente(tenantId, fecha);
    }

    @Override
    public Mono<Long> countTardesByFecha(UUID tenantId, LocalDate fecha) {
        return springRepository.countByTenantAndFechaAndEstadoTarde(tenantId, fecha);
    }

    @Override
    public Mono<Long> countFaltasByFecha(UUID tenantId, LocalDate fecha) {
        return springRepository.countByTenantAndFechaAndEstadoFalta(tenantId, fecha);
    }

    @Override
    public Mono<Long> countJustificadosByFecha(UUID tenantId, LocalDate fecha) {
        return springRepository.countByTenantAndFechaAndEstadoJustificado(tenantId, fecha);
    }

    @Override
    public Mono<Integer> sumMinutosTardanzaByFecha(UUID tenantId, LocalDate fecha) {
        return springRepository.sumMinutosTardanzaByTenantAndFecha(tenantId, fecha);
    }

    @Override
    public Mono<Integer> sumMinutosTardanzaByPersonaIdAndMes(UUID tenantId, UUID personaId, int year, int month) {
        return springRepository.sumMinutosTardanzaByTenantAndPersonaIdAndMes(tenantId, personaId, year, month);
    }

    @Override
    public Flux<RegistroAsistencia> findByFechaBetweenAndEstadoIn(UUID tenantId, LocalDate from, LocalDate to, String estado1, String estado2) {
        return springRepository.findByTenantAndFechaBetweenAndEstadoIn(tenantId, from, to, estado1, estado2)
                .map(AttendanceEntityMapper::toDomain);
    }

}
