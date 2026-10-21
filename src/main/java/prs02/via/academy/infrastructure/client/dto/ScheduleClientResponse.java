package prs02.via.academy.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleClientResponse {

    private UUID id;

    @JsonProperty("personaId")
    private UUID personId;

    @JsonProperty("personName")
    private String personName;

    @JsonProperty("fechaInicio")
    private LocalDate startDate;

    @JsonProperty("fechaFin")
    private LocalDate endDate;

    @JsonProperty("vigente")
    private Boolean active;

    @JsonProperty("observacion")
    private String observation;

    @JsonProperty("creadoEn")
    private LocalDateTime createdAt;

    @JsonProperty("detalles")
    private List<ScheduleDetailResponse> details;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleDetailResponse {

        private UUID id;

        @JsonProperty("diaSemana")
        private String dayOfWeek;

        @JsonProperty("turno")
        private String turno;

        @JsonProperty("horaEntrada")
        private LocalTime entryTime;

        @JsonProperty("horaSalida")
        private LocalTime exitTime;

        @JsonProperty("esLaborable")
        private Boolean isWorkday;
    }
}
