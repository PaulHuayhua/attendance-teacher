package prs02.via.academy.infrastructure.adapters.input.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherRecoveryAlertDTO {

    @JsonProperty("personaId")
    private UUID personaId;

    @JsonProperty("personaName")
    private String personaName;

    @JsonProperty("totalRegistros")
    private int totalRegistros;

    @JsonProperty("totalTardanzas")
    private int totalTardanzas;

    @JsonProperty("totalFaltas")
    private int totalFaltas;

    @JsonProperty("minutosTardanzaAcumulados")
    private int minutosTardanzaAcumulados;

    @JsonProperty("horasARecuperar")
    private double horasARecuperar;

    @JsonProperty("requiereRecuperacion")
    private boolean requiereRecuperacion;
}
