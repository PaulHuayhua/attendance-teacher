package prs02.via.academy.infrastructure.adapters.output;

import prs02.via.academy.domain.model.*;

import java.time.LocalDateTime;

public class AttendanceEntityMapper {

    public static RegistroAsistenciaEntity toEntity(RegistroAsistencia record) {
        return RegistroAsistenciaEntity.builder()
                .id(record.getId())
                .personaId(record.getPersonaId())
                .detalleHorarioId(record.getDetalleHorarioId())
                .fecha(record.getFecha())
                .diaSemana(record.getDiaSemana())
                .turno(record.getTurno())
                .horaEntradaEsperada(record.getHoraEntradaEsperada())
                .horaEntradaReal(record.getHoraEntradaReal())
                .horaSalidaReal(record.getHoraSalidaReal())
                .minutosTardanza(record.getMinutosTardanza())
                .minutosTrabajados(record.getMinutosTrabajados())
                .estado(record.getEstado())
                .justificado(record.getJustificado())
                .motivoJustificacion(record.getMotivoJustificacion())
                .fechaJustificacion(record.getFechaJustificacion())
                .usuarioJustificacion(record.getUsuarioJustificacion())
                .idTenant(record.getIdTenant())
                .calculadoEn(record.getCalculadoEn() != null ? record.getCalculadoEn() : LocalDateTime.now())
                .build();
    }

    public static RegistroAsistencia toDomain(RegistroAsistenciaEntity entity) {
        return RegistroAsistencia.builder()
                .id(entity.getId())
                .personaId(entity.getPersonaId())
                .detalleHorarioId(entity.getDetalleHorarioId())
                .fecha(entity.getFecha())
                .diaSemana(entity.getDiaSemana())
                .turno(entity.getTurno())
                .horaEntradaEsperada(entity.getHoraEntradaEsperada())
                .horaEntradaReal(entity.getHoraEntradaReal())
                .horaSalidaReal(entity.getHoraSalidaReal())
                .minutosTardanza(entity.getMinutosTardanza())
                .minutosTrabajados(entity.getMinutosTrabajados())
                .estado(entity.getEstado())
                .justificado(entity.getJustificado())
                .motivoJustificacion(entity.getMotivoJustificacion())
                .fechaJustificacion(entity.getFechaJustificacion())
                .usuarioJustificacion(entity.getUsuarioJustificacion())
                .idTenant(entity.getIdTenant())
                .calculadoEn(entity.getCalculadoEn())
                .build();
    }

    public static MarcacionBiometricaEntity toEntity(MarcacionBiometrica mark) {
        return MarcacionBiometricaEntity.builder()
                .id(mark.getId())
                .personaId(mark.getPersonaId())
                .idTenant(mark.getIdTenant())
                .dispositivoId(mark.getDispositivoId())
                .fechaHora(mark.getFechaHora())
                .rawData(mark.getRawData())
                .procesado(mark.getProcesado())
                .registradoEn(mark.getRegistradoEn() != null ? mark.getRegistradoEn() : LocalDateTime.now())
                .build();
    }

    public static MarcacionBiometrica toDomain(MarcacionBiometricaEntity entity) {
        return MarcacionBiometrica.builder()
                .id(entity.getId())
                .personaId(entity.getPersonaId())
                .idTenant(entity.getIdTenant())
                .dispositivoId(entity.getDispositivoId())
                .fechaHora(entity.getFechaHora())
                .rawData(entity.getRawData())
                .procesado(entity.getProcesado())
                .registradoEn(entity.getRegistradoEn())
                .build();
    }

    public static AsistenciaMarcacionesEntity toEntity(MarcacionAsistencia relation) {
        return AsistenciaMarcacionesEntity.builder()
                .id(relation.getId())
                .registroAsistenciaId(relation.getRegistroAsistenciaId())
                .marcacionId(relation.getMarcacionId())
                .rolMarcacion(relation.getRolMarcacion())
                .build();
    }

    public static MarcacionAsistencia toDomain(AsistenciaMarcacionesEntity entity) {
        return MarcacionAsistencia.builder()
                .id(entity.getId())
                .registroAsistenciaId(entity.getRegistroAsistenciaId())
                .marcacionId(entity.getMarcacionId())
                .rolMarcacion(entity.getRolMarcacion())
                .build();
    }
}
