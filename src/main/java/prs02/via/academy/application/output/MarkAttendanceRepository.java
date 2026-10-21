package prs02.via.academy.application.output;

import prs02.via.academy.domain.model.MarcacionAsistencia;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface MarkAttendanceRepository {

    Mono<MarcacionAsistencia> save(MarcacionAsistencia relation);

    Mono<Void> deleteByRegistroAsistenciaId(UUID registroAsistenciaId);
}
