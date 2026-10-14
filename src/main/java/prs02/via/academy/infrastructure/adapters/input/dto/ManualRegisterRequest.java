package prs02.via.academy.infrastructure.adapters.input.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualRegisterRequest {

    @NotNull(message = "El ID de persona es obligatorio")
    @JsonProperty("personaId")
    private String personaId;

    @NotNull(message = "La fecha es obligatoria")
    @JsonProperty("fecha")
    private String fecha;

    @NotNull(message = "El turno es obligatorio")
    @JsonProperty("turno")
    private String turno;

    @NotNull(message = "El estado es obligatorio")
    @JsonProperty("estado")
    private String estado;

    @JsonProperty("horaEntradaReal")
    private String horaEntradaReal;

    @JsonProperty("horaEntradaEsperada")
    private String horaEntradaEsperada;

    @JsonProperty("diaSemana")
    private String diaSemana;
}
