package prs02.via.academy.domain.service;

import prs02.via.academy.domain.exception.InvalidAttendanceException;
import prs02.via.academy.domain.model.*;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class AttendanceDomainService {

    public static final int VENTANA_HORAS = 4;

    public static void validateBiometricMark(MarcacionBiometrica mark) {
        if (mark.getPersonaId() == null) {
            throw new InvalidAttendanceException("El ID de persona es obligatorio en la marcación");
        }
        if (mark.getFechaHora() == null) {
            throw new InvalidAttendanceException("La fecha y hora de la marcación es obligatoria");
        }
        if (mark.getFechaHora().toLocalDate().isAfter(LocalDate.now())) {
            throw new InvalidAttendanceException("No se pueden procesar marcaciones futuras");
        }
    }

    public static void validateAttendanceRecord(RegistroAsistencia record) {
        if (record.getPersonaId() == null) {
            throw new InvalidAttendanceException("El ID de persona es obligatorio");
        }
        if (record.getFecha() == null) {
            throw new InvalidAttendanceException("La fecha es obligatoria");
        }
        if (record.getEstado() == null || record.getEstado().isBlank()) {
            throw new InvalidAttendanceException("El estado de asistencia es obligatorio");
        }
        if (record.getMinutosTardanza() != null && record.getMinutosTardanza() < 0) {
            throw new InvalidAttendanceException("Los minutos de tardanza no pueden ser negativos");
        }
    }

    public static String mapDayOfWeekToSpanish(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "LUNES";
            case TUESDAY -> "MARTES";
            case WEDNESDAY -> "MIERCOLES";
            case THURSDAY -> "JUEVES";
            case FRIDAY -> "VIERNES";
            case SATURDAY -> "SABADO";
            case SUNDAY -> "DOMINGO";
        };
    }

    public static boolean isWithinWindow(LocalTime markTime, LocalTime expectedTime) {
        if (markTime == null || expectedTime == null) return false;
        return !markTime.isBefore(expectedTime) && !markTime.isAfter(expectedTime.plusHours(VENTANA_HORAS));
    }

    public static RegistroAsistencia calculateAttendance(
            UUID personaId,
            HorarioDetalleDTO horarioDetalle,
            LocalDate fecha,
            List<MarcacionBiometrica> marks) {

        if (horarioDetalle == null || !Boolean.TRUE.equals(horarioDetalle.getEsLaborable())) {
            return null;
        }

        LocalTime expectedTime = horarioDetalle.getHoraEntrada();
        String diaSemana = horarioDetalle.getDiaSemana();
        String turno = horarioDetalle.getTurno();

        MarcacionBiometrica selectedMark = marks.stream()
                .filter(m -> isWithinWindow(m.getFechaHora().toLocalTime(), expectedTime))
                .min((a, b) -> a.getFechaHora().compareTo(b.getFechaHora()))
                .orElse(null);

        String estado;
        LocalTime horaReal = null;
        Integer minutosTardanza = 0;

        if (selectedMark != null) {
            horaReal = selectedMark.getFechaHora().toLocalTime();
            long diffMinutes = Duration.between(expectedTime, horaReal).toMinutes();
            minutosTardanza = (int) Math.max(0, diffMinutes);
            estado = minutosTardanza > 0 ? "TARDE" : "PRESENTE";
        } else {
            estado = "FALTA";
            minutosTardanza = 0;
        }

        int minutosTrabajados = 0;
        if (selectedMark != null && horarioDetalle.getHoraEntrada() != null && horarioDetalle.getHoraSalida() != null) {
            minutosTrabajados = (int) Duration.between(horarioDetalle.getHoraEntrada(), horarioDetalle.getHoraSalida()).toMinutes();
        }

        return RegistroAsistencia.builder()
                .personaId(personaId)
                .fecha(fecha)
                .diaSemana(diaSemana)
                .turno(turno)
                .horaEntradaEsperada(expectedTime)
                .horaEntradaReal(horaReal)
                .minutosTardanza(minutosTardanza)
                .minutosTrabajados(minutosTrabajados)
                .estado(estado)
                .justificado(false)
                .build();
    }

    public static void validateJustification(RegistroAsistencia record, String motivo, String usuario) {
        if (record == null) {
            throw new InvalidAttendanceException("No se encontró el registro de asistencia para justificar");
        }
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new InvalidAttendanceException("El motivo de justificación es requerido");
        }
        if (usuario == null || usuario.trim().isEmpty()) {
            throw new InvalidAttendanceException("El usuario que justifica es requerido");
        }
        if (Boolean.TRUE.equals(record.getJustificado())) {
            throw new InvalidAttendanceException("La asistencia ya se encuentra justificada");
        }
    }

    public static RegistroAsistencia justifyAttendance(RegistroAsistencia record, String motivo, String usuario) {
        record.setJustificado(true);
        record.setEstado("JUSTIFICADO");
        record.setMotivoJustificacion(motivo);
        record.setFechaJustificacion(LocalDateTime.now());
        record.setUsuarioJustificacion(usuario);
        return record;
    }

    public static boolean isWindowExpired(LocalTime expectedTime) {
        return LocalTime.now().isAfter(expectedTime.plusHours(VENTANA_HORAS));
    }
}
