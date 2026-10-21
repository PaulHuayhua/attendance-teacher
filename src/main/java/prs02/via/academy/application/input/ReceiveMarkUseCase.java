package prs02.via.academy.application.input;

import prs02.via.academy.domain.model.MarcacionBiometrica;
import reactor.core.publisher.Mono;

public interface ReceiveMarkUseCase {
    Mono<MarcacionBiometrica> receiveMark(MarcacionBiometrica mark);
}
