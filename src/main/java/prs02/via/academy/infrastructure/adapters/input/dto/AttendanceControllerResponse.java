package prs02.via.academy.infrastructure.adapters.input.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class AttendanceControllerResponse {

    private UUID id;

    @JsonProperty("personaId")
    private UUID personaId;

    @JsonProperty("personaName")
    private String personaName;

    @JsonProperty("fecha")
    private LocalDate fecha;

    @JsonProperty("diaSemana")
    private String diaSemana;

    @JsonProperty("turno")
    private String turno;

    @JsonProperty("horaEntradaEsperada")
    private LocalTime horaEntradaEsperada;

    @JsonProperty("horaEntradaReal")
    private LocalTime horaEntradaReal;

    @JsonProperty("minutosTardanza")
    private Integer minutosTardanza;

    @JsonProperty("minutosTrabajados")
    private Integer minutosTrabajados;

    @JsonProperty("horaSalidaReal")
    private LocalTime horaSalidaReal;

    @JsonProperty("estado")
    private String estado;

    @JsonProperty("justificado")
    private Boolean justificado;

    @JsonProperty("motivoJustificacion")
    private String motivoJustificacion;

    @JsonProperty("fechaJustificacion")
    private LocalDateTime fechaJustificacion;

    @JsonProperty("usuarioJustificacion")
    private String usuarioJustificacion;

    @JsonProperty("idTenant")
    private UUID idTenant;
}
