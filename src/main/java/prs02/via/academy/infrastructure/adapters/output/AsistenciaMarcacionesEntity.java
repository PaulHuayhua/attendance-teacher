package prs02.via.academy.infrastructure.adapters.output;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("asistencia_marcaciones")
public class AsistenciaMarcacionesEntity {

    @Id
    private UUID id;

    @Column("registro_asistencia_id")
    private UUID registroAsistenciaId;

    @Column("marcacion_id")
    private UUID marcacionId;

    @Column("rol_marcacion")
    private String rolMarcacion;
}
