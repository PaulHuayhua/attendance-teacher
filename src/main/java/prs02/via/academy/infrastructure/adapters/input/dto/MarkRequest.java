package prs02.via.academy.infrastructure.adapters.input.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkRequest {

    @NotNull(message = "El ID de persona es obligatorio")
    @JsonProperty("personaId")
    private String personaId;

    @NotNull(message = "La fecha y hora es obligatoria")
    @JsonProperty("fechaHora")
    private String fechaHora;
}
