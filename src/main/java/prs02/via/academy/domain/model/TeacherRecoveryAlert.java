package prs02.via.academy.domain.model;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherRecoveryAlert {
    private UUID personaId;
    private String personaName;
    private int totalRegistros;
    private int totalTardanzas;
    private int totalFaltas;
    private int minutosTardanzaAcumulados;
    private double horasARecuperar;
    private boolean requiereRecuperacion;
}
