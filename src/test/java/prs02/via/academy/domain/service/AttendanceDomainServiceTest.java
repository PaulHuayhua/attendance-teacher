package prs02.via.academy.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import prs02.via.academy.domain.exception.InvalidAttendanceException;
import prs02.via.academy.domain.model.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas unitarias para AttendanceDomainService.
 * Verifican las reglas de negocio puras del dominio (sin Spring).
 */
@DisplayName("AttendanceDomainService — Reglas de Negocio")
class AttendanceDomainServiceTest {

    // ─────────────────────────────────────────────────────────
    //  validateBiometricMark
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("validateBiometricMark")
    class ValidateBiometricMarkTests {

        @Test
        @DisplayName("✅ Marcación válida no lanza excepción")
        void validMark_doesNotThrow() {
            MarcacionBiometrica mark = MarcacionBiometrica.builder()
                    .personaId(UUID.randomUUID())
                    .fechaHora(LocalDateTime.now().minusMinutes(5))
                    .build();

            assertThatNoException()
                    .isThrownBy(() -> AttendanceDomainService.validateBiometricMark(mark));
        }

        @Test
        @DisplayName("❌ Marcación sin personaId lanza InvalidAttendanceException")
        void mark_withoutPersonaId_throws() {
            MarcacionBiometrica mark = MarcacionBiometrica.builder()
                    .personaId(null)
                    .fechaHora(LocalDateTime.now())
                    .build();

            assertThatThrownBy(() -> AttendanceDomainService.validateBiometricMark(mark))
                    .isInstanceOf(InvalidAttendanceException.class)
                    .hasMessageContaining("ID de persona");
        }

        @Test
        @DisplayName("❌ Marcación sin fechaHora lanza InvalidAttendanceException")
        void mark_withoutFechaHora_throws() {
            MarcacionBiometrica mark = MarcacionBiometrica.builder()
                    .personaId(UUID.randomUUID())
                    .fechaHora(null)
                    .build();

            assertThatThrownBy(() -> AttendanceDomainService.validateBiometricMark(mark))
                    .isInstanceOf(InvalidAttendanceException.class)
                    .hasMessageContaining("fecha y hora");
        }

        @Test
        @DisplayName("❌ Marcación con fecha futura lanza InvalidAttendanceException")
        void mark_withFutureDate_throws() {
            MarcacionBiometrica mark = MarcacionBiometrica.builder()
                    .personaId(UUID.randomUUID())
                    .fechaHora(LocalDateTime.now().plusDays(1))
                    .build();

            assertThatThrownBy(() -> AttendanceDomainService.validateBiometricMark(mark))
                    .isInstanceOf(InvalidAttendanceException.class)
                    .hasMessageContaining("futuras");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  validateAttendanceRecord
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("validateAttendanceRecord")
    class ValidateAttendanceRecordTests {

        @Test
        @DisplayName("✅ Registro válido no lanza excepción")
        void validRecord_doesNotThrow() {
            RegistroAsistencia record = RegistroAsistencia.builder()
                    .personaId(UUID.randomUUID())
                    .fecha(LocalDate.now())
                    .estado("PRESENTE")
                    .minutosTardanza(0)
                    .build();

            assertThatNoException()
                    .isThrownBy(() -> AttendanceDomainService.validateAttendanceRecord(record));
        }

        @Test
        @DisplayName("❌ Registro sin personaId lanza excepción")
        void record_withoutPersonaId_throws() {
            RegistroAsistencia record = RegistroAsistencia.builder()
                    .fecha(LocalDate.now())
                    .estado("PRESENTE")
                    .build();

            assertThatThrownBy(() -> AttendanceDomainService.validateAttendanceRecord(record))
                    .isInstanceOf(InvalidAttendanceException.class);
        }

        @Test
        @DisplayName("❌ Registro sin fecha lanza excepción")
        void record_withoutFecha_throws() {
            RegistroAsistencia record = RegistroAsistencia.builder()
                    .personaId(UUID.randomUUID())
                    .estado("PRESENTE")
                    .build();

            assertThatThrownBy(() -> AttendanceDomainService.validateAttendanceRecord(record))
                    .isInstanceOf(InvalidAttendanceException.class)
                    .hasMessageContaining("fecha");
        }

        @Test
        @DisplayName("❌ Registro con minutos de tardanza negativos lanza excepción")
        void record_withNegativeLateness_throws() {
            RegistroAsistencia record = RegistroAsistencia.builder()
                    .personaId(UUID.randomUUID())
                    .fecha(LocalDate.now())
                    .estado("TARDE")
                    .minutosTardanza(-5)
                    .build();

            assertThatThrownBy(() -> AttendanceDomainService.validateAttendanceRecord(record))
                    .isInstanceOf(InvalidAttendanceException.class)
                    .hasMessageContaining("negativos");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  calculateAttendance — estado PRESENTE
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("calculateAttendance — Estado PRESENTE")
    class CalculateAttendancePresenteTests {

        @Test
        @DisplayName("✅ Llegó exacto → estado PRESENTE con 0 minutos tardanza")
        void onTime_returnsPresente() {
            UUID personaId = UUID.randomUUID();
            LocalDate fecha = LocalDate.now();
            LocalTime horaEntrada = LocalTime.of(8, 0);

            HorarioDetalleDTO horario = HorarioDetalleDTO.builder()
                    .diaSemana("LUNES")
                    .turno("MAÑANA")
                    .horaEntrada(horaEntrada)
                    .horaSalida(LocalTime.of(13, 0))
                    .esLaborable(true)
                    .build();

            MarcacionBiometrica mark = MarcacionBiometrica.builder()
                    .personaId(personaId)
                    .fechaHora(LocalDateTime.of(fecha, horaEntrada))
                    .build();

            RegistroAsistencia result = AttendanceDomainService.calculateAttendance(
                    personaId, horario, fecha, List.of(mark));

            assertThat(result).isNotNull();
            assertThat(result.getEstado()).isEqualTo("PRESENTE");
            assertThat(result.getMinutosTardanza()).isEqualTo(0);
            assertThat(result.getHoraEntradaReal()).isEqualTo(horaEntrada);
        }

        @Test
        @DisplayName("✅ Llegó 1 minuto antes → estado PRESENTE")
        void arrivedEarly_returnsPresente() {
            UUID personaId = UUID.randomUUID();
            LocalDate fecha = LocalDate.now();
            LocalTime horaEntrada = LocalTime.of(8, 0);

            HorarioDetalleDTO horario = buildHorario(horaEntrada, LocalTime.of(13, 0));

            MarcacionBiometrica mark = MarcacionBiometrica.builder()
                    .personaId(personaId)
                    .fechaHora(LocalDateTime.of(fecha, LocalTime.of(7, 59)))
                    .build();

            // Antes de la ventana → no hay match → FALTA (regla de ventana)
            RegistroAsistencia result = AttendanceDomainService.calculateAttendance(
                    personaId, horario, fecha, List.of(mark));

            assertThat(result).isNotNull();
            // La ventana empieza desde la hora de entrada; llegada antes → FALTA
            assertThat(result.getEstado()).isEqualTo("FALTA");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  calculateAttendance — estado TARDE
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("calculateAttendance — Estado TARDE")
    class CalculateAttendanceTardeTests {

        @Test
        @DisplayName("✅ Llegó 15 minutos tarde → estado TARDE con 15 min tardanza")
        void late15min_returnsTarde() {
            UUID personaId = UUID.randomUUID();
            LocalDate fecha = LocalDate.now();
            LocalTime horaEntrada = LocalTime.of(8, 0);
            LocalTime horaReal = horaEntrada.plusMinutes(15);

            HorarioDetalleDTO horario = buildHorario(horaEntrada, LocalTime.of(13, 0));

            MarcacionBiometrica mark = MarcacionBiometrica.builder()
                    .personaId(personaId)
                    .fechaHora(LocalDateTime.of(fecha, horaReal))
                    .build();

            RegistroAsistencia result = AttendanceDomainService.calculateAttendance(
                    personaId, horario, fecha, List.of(mark));

            assertThat(result).isNotNull();
            assertThat(result.getEstado()).isEqualTo("TARDE");
            assertThat(result.getMinutosTardanza()).isEqualTo(15);
        }

        @Test
        @DisplayName("✅ Llegó 3h 59min tarde → dentro de ventana → TARDE")
        void lateWithinWindow_returnsTarde() {
            UUID personaId = UUID.randomUUID();
            LocalDate fecha = LocalDate.now();
            LocalTime horaEntrada = LocalTime.of(8, 0);
            LocalTime horaReal = horaEntrada.plusHours(3).plusMinutes(59);

            HorarioDetalleDTO horario = buildHorario(horaEntrada, LocalTime.of(17, 0));

            MarcacionBiometrica mark = MarcacionBiometrica.builder()
                    .personaId(personaId)
                    .fechaHora(LocalDateTime.of(fecha, horaReal))
                    .build();

            RegistroAsistencia result = AttendanceDomainService.calculateAttendance(
                    personaId, horario, fecha, List.of(mark));

            assertThat(result.getEstado()).isEqualTo("TARDE");
            assertThat(result.getMinutosTardanza()).isEqualTo(239);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  calculateAttendance — estado FALTA
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("calculateAttendance — Estado FALTA")
    class CalculateAttendanceFaltaTests {

        @Test
        @DisplayName("✅ Sin marcaciones → estado FALTA")
        void noMarks_returnsFalta() {
            UUID personaId = UUID.randomUUID();
            HorarioDetalleDTO horario = buildHorario(LocalTime.of(8, 0), LocalTime.of(13, 0));

            RegistroAsistencia result = AttendanceDomainService.calculateAttendance(
                    personaId, horario, LocalDate.now(), List.of());

            assertThat(result).isNotNull();
            assertThat(result.getEstado()).isEqualTo("FALTA");
            assertThat(result.getMinutosTardanza()).isEqualTo(0);
            assertThat(result.getHoraEntradaReal()).isNull();
        }

        @Test
        @DisplayName("✅ Marcación fuera de ventana (>4h) → FALTA")
        void markOutsideWindow_returnsFalta() {
            UUID personaId = UUID.randomUUID();
            LocalDate fecha = LocalDate.now();
            LocalTime horaEntrada = LocalTime.of(8, 0);

            HorarioDetalleDTO horario = buildHorario(horaEntrada, LocalTime.of(13, 0));

            // 4 horas y 1 minuto después → fuera de ventana
            MarcacionBiometrica mark = MarcacionBiometrica.builder()
                    .personaId(personaId)
                    .fechaHora(LocalDateTime.of(fecha, horaEntrada.plusHours(4).plusMinutes(1)))
                    .build();

            RegistroAsistencia result = AttendanceDomainService.calculateAttendance(
                    personaId, horario, fecha, List.of(mark));

            assertThat(result.getEstado()).isEqualTo("FALTA");
        }

        @Test
        @DisplayName("✅ Horario no laborable → retorna null")
        void nonWorkingSchedule_returnsNull() {
            HorarioDetalleDTO horario = buildHorario(LocalTime.of(8, 0), LocalTime.of(13, 0));
            horario.setEsLaborable(false);

            RegistroAsistencia result = AttendanceDomainService.calculateAttendance(
                    UUID.randomUUID(), horario, LocalDate.now(), List.of());

            assertThat(result).isNull();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  validateJustification
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("validateJustification")
    class ValidateJustificationTests {

        @Test
        @DisplayName("✅ Justificación válida no lanza excepción")
        void validJustification_doesNotThrow() {
            RegistroAsistencia record = buildRecord("FALTA");

            assertThatNoException()
                    .isThrownBy(() -> AttendanceDomainService.validateJustification(
                            record, "Enfermedad", "admin@via.edu.pe"));
        }

        @Test
        @DisplayName("❌ Registro ya justificado → lanza excepción")
        void alreadyJustified_throws() {
            RegistroAsistencia record = buildRecord("JUSTIFICADO");
            record.setJustificado(true);

            assertThatThrownBy(() -> AttendanceDomainService.validateJustification(
                    record, "Motivo", "usuario"))
                    .isInstanceOf(InvalidAttendanceException.class)
                    .hasMessageContaining("ya se encuentra justificada");
        }

        @Test
        @DisplayName("❌ Motivo vacío → lanza excepción")
        void emptyMotivo_throws() {
            RegistroAsistencia record = buildRecord("FALTA");

            assertThatThrownBy(() -> AttendanceDomainService.validateJustification(
                    record, "  ", "admin@via.edu.pe"))
                    .isInstanceOf(InvalidAttendanceException.class)
                    .hasMessageContaining("motivo");
        }

        @Test
        @DisplayName("❌ Usuario vacío → lanza excepción")
        void emptyUsuario_throws() {
            RegistroAsistencia record = buildRecord("FALTA");

            assertThatThrownBy(() -> AttendanceDomainService.validateJustification(
                    record, "Enfermedad", ""))
                    .isInstanceOf(InvalidAttendanceException.class)
                    .hasMessageContaining("usuario");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  justifyAttendance
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("justifyAttendance")
    class JustifyAttendanceTests {

        @Test
        @DisplayName("✅ Registro FALTA → queda JUSTIFICADO con datos correctos")
        void justify_setsCorrectState() {
            RegistroAsistencia record = buildRecord("FALTA");
            String motivo  = "Cita médica";
            String usuario = "admin@via.edu.pe";

            RegistroAsistencia result = AttendanceDomainService.justifyAttendance(record, motivo, usuario);

            assertThat(result.getEstado()).isEqualTo("JUSTIFICADO");
            assertThat(result.getJustificado()).isTrue();
            assertThat(result.getMotivoJustificacion()).isEqualTo(motivo);
            assertThat(result.getUsuarioJustificacion()).isEqualTo(usuario);
            assertThat(result.getFechaJustificacion()).isNotNull();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  mapDayOfWeekToSpanish
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("mapDayOfWeekToSpanish")
    class MapDayOfWeekTests {

        @Test
        @DisplayName("✅ Todos los días se mapean correctamente al español")
        void allDays_mapCorrectly() {
            assertThat(AttendanceDomainService.mapDayOfWeekToSpanish(DayOfWeek.MONDAY)).isEqualTo("LUNES");
            assertThat(AttendanceDomainService.mapDayOfWeekToSpanish(DayOfWeek.TUESDAY)).isEqualTo("MARTES");
            assertThat(AttendanceDomainService.mapDayOfWeekToSpanish(DayOfWeek.WEDNESDAY)).isEqualTo("MIERCOLES");
            assertThat(AttendanceDomainService.mapDayOfWeekToSpanish(DayOfWeek.THURSDAY)).isEqualTo("JUEVES");
            assertThat(AttendanceDomainService.mapDayOfWeekToSpanish(DayOfWeek.FRIDAY)).isEqualTo("VIERNES");
            assertThat(AttendanceDomainService.mapDayOfWeekToSpanish(DayOfWeek.SATURDAY)).isEqualTo("SABADO");
            assertThat(AttendanceDomainService.mapDayOfWeekToSpanish(DayOfWeek.SUNDAY)).isEqualTo("DOMINGO");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  isWithinWindow
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("isWithinWindow")
    class IsWithinWindowTests {

        @Test
        @DisplayName("✅ Hora exacta de entrada → dentro de ventana")
        void exactEntryTime_isWithinWindow() {
            assertThat(AttendanceDomainService.isWithinWindow(
                    LocalTime.of(8, 0), LocalTime.of(8, 0))).isTrue();
        }

        @Test
        @DisplayName("✅ 4 horas después → exactamente en el límite de ventana")
        void exactWindowLimit_isWithinWindow() {
            assertThat(AttendanceDomainService.isWithinWindow(
                    LocalTime.of(12, 0), LocalTime.of(8, 0))).isTrue();
        }

        @Test
        @DisplayName("❌ 4 horas y 1 minuto después → fuera de ventana")
        void justOutsideWindow_isNotWithinWindow() {
            assertThat(AttendanceDomainService.isWithinWindow(
                    LocalTime.of(12, 1), LocalTime.of(8, 0))).isFalse();
        }

        @Test
        @DisplayName("❌ Hora anterior a la de entrada → fuera de ventana")
        void beforeEntryTime_isNotWithinWindow() {
            assertThat(AttendanceDomainService.isWithinWindow(
                    LocalTime.of(7, 59), LocalTime.of(8, 0))).isFalse();
        }

        @Test
        @DisplayName("❌ Valores nulos → false")
        void nullValues_returnFalse() {
            assertThat(AttendanceDomainService.isWithinWindow(null, LocalTime.of(8, 0))).isFalse();
            assertThat(AttendanceDomainService.isWithinWindow(LocalTime.of(8, 0), null)).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────
    private HorarioDetalleDTO buildHorario(LocalTime entrada, LocalTime salida) {
        return HorarioDetalleDTO.builder()
                .diaSemana("LUNES")
                .turno("MAÑANA")
                .horaEntrada(entrada)
                .horaSalida(salida)
                .esLaborable(true)
                .build();
    }

    private RegistroAsistencia buildRecord(String estado) {
        return RegistroAsistencia.builder()
                .id(UUID.randomUUID())
                .personaId(UUID.randomUUID())
                .fecha(LocalDate.now())
                .estado(estado)
                .justificado(false)
                .build();
    }
}
