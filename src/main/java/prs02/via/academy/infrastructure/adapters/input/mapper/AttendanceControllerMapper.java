package prs02.via.academy.infrastructure.adapters.input.mapper;

import prs02.via.academy.domain.model.MarcacionBiometrica;
import prs02.via.academy.domain.model.RegistroAsistencia;
import prs02.via.academy.domain.service.AttendanceDomainService;
import prs02.via.academy.infrastructure.adapters.input.dto.AttendanceControllerResponse;
import prs02.via.academy.infrastructure.adapters.input.dto.ManualRegisterRequest;
import prs02.via.academy.infrastructure.adapters.input.dto.MarkRequest;
import prs02.via.academy.infrastructure.client.dto.UserClientResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public class AttendanceControllerMapper {

    public static AttendanceControllerResponse toControllerResponse(RegistroAsistencia record) {
        return AttendanceControllerResponse.builder()
                .id(record.getId())
                .personaId(record.getPersonaId())
                .personaName("Profesor " + record.getPersonaId().toString().substring(0, 8))
                .fecha(record.getFecha())
                .diaSemana(record.getDiaSemana())
                .turno(record.getTurno())
                .horaEntradaEsperada(record.getHoraEntradaEsperada())
                .horaEntradaReal(record.getHoraEntradaReal())
                .minutosTardanza(record.getMinutosTardanza())
                .minutosTrabajados(record.getMinutosTrabajados())
                .horaSalidaReal(record.getHoraSalidaReal())
                .estado(record.getEstado())
                .justificado(record.getJustificado())
                .motivoJustificacion(record.getMotivoJustificacion())
                .fechaJustificacion(record.getFechaJustificacion())
                .usuarioJustificacion(record.getUsuarioJustificacion())
                .idTenant(record.getIdTenant())
                .build();
    }

    public static AttendanceControllerResponse toControllerResponse(RegistroAsistencia record, UserClientResponse user) {
        AttendanceControllerResponse response = toControllerResponse(record);
        if (user != null) {
            String fullName = user.getFullName();
            if (fullName == null || fullName.isBlank()) {
                fullName = ((user.getFirstName() != null ? user.getFirstName() : "")
                        + " " + (user.getLastName() != null ? user.getLastName() : "")).trim();
            }
            if (!fullName.isBlank()) {
                response.setPersonaName(fullName);
            }
        }
        return response;
    }

    public static MarcacionBiometrica toDomain(MarkRequest request) {
        return MarcacionBiometrica.builder()
                .personaId(UUID.fromString(request.getPersonaId()))
                .fechaHora(LocalDateTime.parse(request.getFechaHora()))
                .build();
    }

    public static RegistroAsistencia toDomain(ManualRegisterRequest request) {
        String diaSemana = request.getDiaSemana() != null ? request.getDiaSemana()
                : AttendanceDomainService.mapDayOfWeekToSpanish(
                        LocalDate.parse(request.getFecha()).getDayOfWeek());

        LocalTime horaEsperada = request.getHoraEntradaEsperada() != null
                ? LocalTime.parse(request.getHoraEntradaEsperada()) : null;

        LocalTime horaReal = request.getHoraEntradaReal() != null
                ? LocalTime.parse(request.getHoraEntradaReal()) : null;

        return RegistroAsistencia.builder()
                .personaId(UUID.fromString(request.getPersonaId()))
                .fecha(LocalDate.parse(request.getFecha()))
                .turno(request.getTurno())
                .estado(request.getEstado())
                .justificado(false)
                .diaSemana(diaSemana)
                .horaEntradaEsperada(horaEsperada)
                .horaEntradaReal(horaReal)
                .build();
    }
}
