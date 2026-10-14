package prs02.via.academy.infrastructure.adapters.output;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SpringAsistenciaMarcacionesRepository extends R2dbcRepository<AsistenciaMarcacionesEntity, UUID> {

    @Modifying
    @Query("DELETE FROM asistencia_marcaciones WHERE registro_asistencia_id = $1")
    Mono<Integer> deleteByRegistroAsistenciaId(UUID registroAsistenciaId);
}
