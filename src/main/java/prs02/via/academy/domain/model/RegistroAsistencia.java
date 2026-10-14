package prs02.via.academy.domain.model;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistroAsistencia {

    private UUID id;
    private UUID personaId;
    private UUID detalleHorarioId;
    private LocalDate fecha;
    private String diaSemana;
    private String turno;
    private LocalTime horaEntradaEsperada;
    private LocalTime horaEntradaReal;
    private LocalTime horaSalidaReal;
    private Integer minutosTardanza;
    private Integer minutosTrabajados;
    private String estado;
    private Boolean justificado;
    private String motivoJustificacion;
    private LocalDateTime fechaJustificacion;
    private String usuarioJustificacion;
    private LocalDateTime calculadoEn;
    private UUID idTenant;
}
