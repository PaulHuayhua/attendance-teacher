package prs02.via.academy.application.output;

import prs02.via.academy.domain.model.RegistroAsistencia;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Puerto de salida para gestionar justificaciones en el microservicio externo
 */
public interface JustificationPort {

    /**
     * Envía una justificación al microservicio de justificaciones
     *
     * @param record Registro de asistencia justificado
     * @param motivo Motivo de la justificación
     * @param usuario Usuario que justifica
     * @return Mono con el ID de la justificación creada
     */
    Mono<UUID> sendJustification(RegistroAsistencia record, String motivo, String usuario);

    /**
     * Consulta el estado de una justificación
     *
     * @param attendanceRecordId ID del registro de asistencia
     * @return Mono con el estado de la justificación
     */
    Mono<String> getJustificationStatus(UUID attendanceRecordId);

    /**
     * Verifica si existe una justificación para un registro de asistencia
     *
     * @param attendanceRecordId ID del registro de asistencia
     * @return true si existe, false si no
     */
    Mono<Boolean> existsJustification(UUID attendanceRecordId);
}
