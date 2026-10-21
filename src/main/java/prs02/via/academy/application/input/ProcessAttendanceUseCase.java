package prs02.via.academy.application.input;

import prs02.via.academy.domain.model.RegistroAsistencia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface ProcessAttendanceUseCase {
    Flux<RegistroAsistencia> processAttendance(UUID tenantId, UUID personaId, LocalDate date);
    Flux<RegistroAsistencia> processAllPending();
}
