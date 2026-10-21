package prs02.via.academy.infrastructure.adapters.input.dto;

import java.time.LocalDateTime;

/**
 * Registro extraído de una línea del archivo biométrico exportado.
 *
 * @param pin      código de empleado / usuario del reloj biométrico
 * @param dateTime fecha y hora de la marcación
 */
public record BiometricFileRecord(String pin, LocalDateTime dateTime) {}
