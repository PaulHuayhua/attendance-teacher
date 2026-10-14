package prs02.via.academy.infrastructure.adapters.input.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JustifyRequest {

    @JsonProperty("justified")
    private Boolean justified;

    @NotBlank(message = "El motivo de justificación es requerido")
    @JsonProperty("motivo")
    private String motivo;
}
