package prs02.via.academy.infrastructure.adapters.output;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface SpringRegistroAsistenciaRepository extends R2dbcRepository<RegistroAsistenciaEntity, UUID> {

    @Query("SELECT * FROM registro_asistencia WHERE id_tenant = $1")
    Flux<RegistroAsistenciaEntity> findAllByTenant(UUID tenantId);

    @Query("SELECT * FROM registro_asistencia WHERE id_tenant = $1 AND persona_id = $2")
    Flux<RegistroAsistenciaEntity> findByTenantAndPersonaId(UUID tenantId, UUID personaId);

    @Query("SELECT * FROM registro_asistencia WHERE id_tenant = $1 AND fecha = $2")
    Flux<RegistroAsistenciaEntity> findByTenantAndFecha(UUID tenantId, LocalDate fecha);

    @Query("SELECT * FROM registro_asistencia WHERE id_tenant = $1 AND persona_id = $2 AND fecha BETWEEN $3 AND $4")
    Flux<RegistroAsistenciaEntity> findByTenantAndPersonaIdAndFechaBetween(UUID tenantId, UUID personaId, LocalDate from, LocalDate to);

    @Query("SELECT * FROM registro_asistencia WHERE id_tenant = $1 AND fecha BETWEEN $2 AND $3")
    Flux<RegistroAsistenciaEntity> findByTenantAndFechaBetween(UUID tenantId, LocalDate from, LocalDate to);

    @Query("SELECT * FROM registro_asistencia WHERE id_tenant = $1 AND persona_id = $2 AND fecha = $3 AND turno = $4")
    Mono<RegistroAsistenciaEntity> findByTenantAndPersonaIdAndFechaAndTurno(UUID tenantId, UUID personaId, LocalDate fecha, String turno);

    @Query("SELECT * FROM registro_asistencia WHERE id_tenant = $1 AND id = $2")
    Mono<RegistroAsistenciaEntity> findByTenantAndId(UUID tenantId, UUID id);

    @Query("SELECT COUNT(*) FROM registro_asistencia WHERE id_tenant = $1 AND fecha = $2 AND estado = 'PRESENTE'")
    Mono<Long> countByTenantAndFechaAndEstadoPresente(UUID tenantId, LocalDate fecha);

    @Query("SELECT COUNT(*) FROM registro_asistencia WHERE id_tenant = $1 AND fecha = $2 AND estado = 'TARDE'")
    Mono<Long> countByTenantAndFechaAndEstadoTarde(UUID tenantId, LocalDate fecha);

    @Query("SELECT COUNT(*) FROM registro_asistencia WHERE id_tenant = $1 AND fecha = $2 AND estado = 'FALTA'")
    Mono<Long> countByTenantAndFechaAndEstadoFalta(UUID tenantId, LocalDate fecha);

    @Query("SELECT COUNT(*) FROM registro_asistencia WHERE id_tenant = $1 AND fecha = $2 AND estado = 'JUSTIFICADO'")
    Mono<Long> countByTenantAndFechaAndEstadoJustificado(UUID tenantId, LocalDate fecha);

    @Query("SELECT COALESCE(SUM(minutos_tardanza), 0) FROM registro_asistencia WHERE id_tenant = $1 AND fecha = $2")
    Mono<Integer> sumMinutosTardanzaByTenantAndFecha(UUID tenantId, LocalDate fecha);

    @Query("SELECT COALESCE(SUM(minutos_tardanza), 0) FROM registro_asistencia WHERE id_tenant = $1 AND persona_id = $2 AND EXTRACT(YEAR FROM fecha) = $3 AND EXTRACT(MONTH FROM fecha) = $4")
    Mono<Integer> sumMinutosTardanzaByTenantAndPersonaIdAndMes(UUID tenantId, UUID personaId, int year, int month);

    @Query("SELECT * FROM registro_asistencia WHERE id_tenant = $1 AND fecha BETWEEN $2 AND $3 AND estado IN ($4, $5)")
    Flux<RegistroAsistenciaEntity> findByTenantAndFechaBetweenAndEstadoIn(UUID tenantId, LocalDate from, LocalDate to, String estado1, String estado2);
}
