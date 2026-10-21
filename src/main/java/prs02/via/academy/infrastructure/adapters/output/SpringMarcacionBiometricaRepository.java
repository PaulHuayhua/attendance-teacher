package prs02.via.academy.infrastructure.adapters.output;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface SpringMarcacionBiometricaRepository extends R2dbcRepository<MarcacionBiometricaEntity, UUID> {

    @Query("SELECT * FROM marcacion_biometrica WHERE id_tenant = $1 AND persona_id = $2 AND DATE(fecha_hora) = $3 AND procesado = false")
    Flux<MarcacionBiometricaEntity> findNoProcesadasByTenantAndPersonaIdAndFecha(UUID tenantId, UUID personaId, LocalDate fecha);

    @Query("SELECT * FROM marcacion_biometrica WHERE id_tenant = $1 AND procesado = false")
    Flux<MarcacionBiometricaEntity> findByTenantAndProcesadoFalse(UUID tenantId);

    @Query("SELECT * FROM marcacion_biometrica WHERE id_tenant = $1 AND persona_id = $2 AND DATE(fecha_hora) = $3")
    Flux<MarcacionBiometricaEntity> findByTenantAndPersonaIdAndFecha(UUID tenantId, UUID personaId, LocalDate fecha);

    @Query("UPDATE marcacion_biometrica SET procesado = true WHERE id_tenant = $1 AND id = $2")
    Mono<Integer> marcarComoProcesadoPorTenantAndId(UUID tenantId, UUID id);

    @Query("SELECT * FROM marcacion_biometrica WHERE id_tenant = $1 AND id = $2")
    Mono<MarcacionBiometricaEntity> findByTenantAndId(UUID tenantId, UUID id);
}
