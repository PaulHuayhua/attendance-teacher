package prs02.via.academy.domain.model;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TardinessReport {
    private UUID personaId;
    private String personaName;
    private String mes;
    private int totalMinutosTardanza;
    private int totalFaltas;
    private int totalAsistencias;
    private int umbral;
    private boolean alertaGenerada;
}
