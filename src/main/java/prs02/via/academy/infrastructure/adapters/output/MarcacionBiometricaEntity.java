package prs02.via.academy.infrastructure.adapters.output;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("marcacion_biometrica")
public class MarcacionBiometricaEntity {

    @Id
    private UUID id;

    @Column("persona_id")
    private UUID personaId;

    @Column("id_tenant")
    private UUID idTenant;

    @Column("dispositivo_id")
    private UUID dispositivoId;

    @Column("fecha_hora")
    private LocalDateTime fechaHora;

    @Column("raw_data")
    private String rawData;

    @Column("procesado")
    private Boolean procesado;

    @Column("registrado_en")
    private LocalDateTime registradoEn;
}
