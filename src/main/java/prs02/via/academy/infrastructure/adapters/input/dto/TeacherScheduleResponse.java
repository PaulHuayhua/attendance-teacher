package prs02.via.academy.infrastructure.adapters.input.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherScheduleResponse {

    @JsonProperty("diaSemana")
    private String diaSemana;

    @JsonProperty("turno")
    private String turno;

    @JsonProperty("horaEntrada")
    private LocalTime horaEntrada;

    @JsonProperty("horaSalida")
    private LocalTime horaSalida;
}
