package prs02.via.academy.infrastructure.adapters.output;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("registro_asistencia")
public class RegistroAsistenciaEntity {

    @Id
    private UUID id;

    @Column("persona_id")
    private UUID personaId;

    @Column("detalle_horario_id")
    private UUID detalleHorarioId;

    @Column("fecha")
    private LocalDate fecha;

    @Column("dia_semana")
    private String diaSemana;

    @Column("turno")
    private String turno;

    @Column("hora_entrada_esperada")
    private LocalTime horaEntradaEsperada;

    @Column("hora_entrada_real")
    private LocalTime horaEntradaReal;

    @Column("hora_salida_real")
    private LocalTime horaSalidaReal;

    @Column("minutos_tardanza")
    private Integer minutosTardanza;

    @Column("minutos_trabajados")
    private Integer minutosTrabajados;

    @Column("estado")
    private String estado;

    @Column("justificado")
    private Boolean justificado;

    @Column("motivo_justificacion")
    private String motivoJustificacion;

    @Column("fecha_justificacion")
    private LocalDateTime fechaJustificacion;

    @Column("usuario_justificacion")
    private String usuarioJustificacion;

    @Column("id_tenant")
    private UUID idTenant;

    @Column("calculado_en")
    private LocalDateTime calculadoEn;
}
