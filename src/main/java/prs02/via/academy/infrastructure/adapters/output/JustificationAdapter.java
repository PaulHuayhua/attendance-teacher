package prs02.via.academy.infrastructure.adapters.output;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.application.output.JustificationPort;
import prs02.via.academy.domain.model.RegistroAsistencia;
import prs02.via.academy.infrastructure.client.JustificationsClient;
import prs02.via.academy.infrastructure.client.UsersClient;
import prs02.via.academy.infrastructure.client.dto.JustificationRequest;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JustificationAdapter implements JustificationPort {

    private final JustificationsClient justificationsClient;
    private final UsersClient usersClient;

    @Override
    public Mono<UUID> sendJustification(RegistroAsistencia record, String motivo, String usuario) {
        log.info("Enviando justificación para registro ID: {}", record.getId());

        return usersClient.getUserByPersonId(record.getPersonaId())
                .flatMap(user -> {
                    JustificationRequest request = JustificationRequest.builder()
                            .staffName(user.getFirstName() != null ? user.getFirstName() : "Docente")
                            .staffLastName(user.getLastName() != null ? user.getLastName() : "")
                            .staffDocumentType("DNI")
                            .staffDocumentNumber("")
                            .position("Docente")
                            .area("Pedagógica")
                            .startDate(record.getFecha().toString())
                            .endDate(record.getFecha().toString())
                            .reason(mapReason(record.getEstado()))
                            // specificReason incluye el motivo + el attendanceRecordId para trazabilidad
                            .specificReason(motivo + " [ref:" + record.getId() + "]")
                            .justificationType("RETROACTIVA")
                            .build();

                    return justificationsClient.createJustification(request)
                            .map(response -> response.getId() != null
                                    ? UUID.fromString(response.getId())
                                    : UUID.randomUUID())
                            .doOnSuccess(id -> log.info("Justificación creada en MS externo: {}", id));
                })
                .onErrorResume(error -> {
                    log.warn("No se pudo enviar al MS de justificaciones (la justificación local sí fue guardada): {}",
                            error.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<String> getJustificationStatus(UUID attendanceRecordId) {
        return justificationsClient.getJustificationByAttendanceRecordId(attendanceRecordId)
                .map(response -> {
                    String status = response.getApprovalStatus();
                    return status != null ? status : "PENDING";
                })
                .onErrorResume(error -> {
                    if (error instanceof ResponseStatusException &&
                            ((ResponseStatusException) error).getStatusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.just("NOT_FOUND");
                    }
                    log.error("Error al consultar estado: {}", error.getMessage());
                    return Mono.just("ERROR");
                });
    }

    @Override
    public Mono<Boolean> existsJustification(UUID attendanceRecordId) {
        return getJustificationStatus(attendanceRecordId)
                .map(status -> !"NOT_FOUND".equals(status) && !"ERROR".equals(status))
                .defaultIfEmpty(false);
    }

    private String mapReason(String estado) {
        if ("FALTA".equals(estado) || "AUSENTE".equals(estado)) return "Enfermedad";
        if ("TARDE".equals(estado)) return "Motivos personales";
        return "Otros";
    }
}
