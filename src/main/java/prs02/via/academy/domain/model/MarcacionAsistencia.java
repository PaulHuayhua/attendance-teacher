package prs02.via.academy.domain.model;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarcacionAsistencia {

    private UUID id;
    private UUID registroAsistenciaId;
    private UUID marcacionId;
    private String rolMarcacion;
}
