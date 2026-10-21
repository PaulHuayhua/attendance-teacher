package prs02.via.academy.application.input;

import prs02.via.academy.domain.model.RegistroAsistencia;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ManualRegisterUseCase {
    Mono<RegistroAsistencia> register(RegistroAsistencia record);
}
