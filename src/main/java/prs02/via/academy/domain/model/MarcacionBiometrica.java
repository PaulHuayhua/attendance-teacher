package prs02.via.academy.domain.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarcacionBiometrica {

    private UUID id;
    private UUID personaId;
    private UUID idTenant;
    private UUID dispositivoId;
    private LocalDateTime fechaHora;
    private String rawData;
    private Boolean procesado;
    private LocalDateTime registradoEn;
}
