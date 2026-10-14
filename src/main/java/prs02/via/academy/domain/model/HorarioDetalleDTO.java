package prs02.via.academy.domain.model;

import lombok.*;

import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorarioDetalleDTO {

    private String diaSemana;
    private String turno;
    private LocalTime horaEntrada;
    private LocalTime horaSalida;
    private Boolean esLaborable;
}
