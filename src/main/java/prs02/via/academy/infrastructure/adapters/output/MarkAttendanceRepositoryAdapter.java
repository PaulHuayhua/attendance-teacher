package prs02.via.academy.infrastructure.adapters.output;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import prs02.via.academy.application.output.MarkAttendanceRepository;
import prs02.via.academy.domain.model.MarcacionAsistencia;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MarkAttendanceRepositoryAdapter implements MarkAttendanceRepository {

    private final SpringAsistenciaMarcacionesRepository springRepository;

    @Override
    @Transactional
    public Mono<MarcacionAsistencia> save(MarcacionAsistencia relation) {
        AsistenciaMarcacionesEntity entity = AttendanceEntityMapper.toEntity(relation);
        return springRepository.save(entity)
                .map(AttendanceEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public Mono<Void> deleteByRegistroAsistenciaId(UUID registroAsistenciaId) {
        return springRepository.deleteByRegistroAsistenciaId(registroAsistenciaId).then();
    }
}
