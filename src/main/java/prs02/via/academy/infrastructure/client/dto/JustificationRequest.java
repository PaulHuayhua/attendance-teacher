package prs02.via.academy.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * DTO que mapea al modelo ProxyJustification del MS de justificaciones.
 * Se envía como parte JSON dentro de un multipart/form-data.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JustificationRequest {

    @JsonProperty("staffName")
    private String staffName;

    @JsonProperty("staffLastName")
    private String staffLastName;

    @JsonProperty("staffDocumentType")
    private String staffDocumentType;

    @JsonProperty("staffDocumentNumber")
    private String staffDocumentNumber;

    @JsonProperty("position")
    private String position;

    @JsonProperty("area")
    private String area;

    @JsonProperty("startDate")
    private String startDate; // "2026-06-30"

    @JsonProperty("endDate")
    private String endDate;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("specificReason")
    private String specificReason;

    @JsonProperty("justificationType")
    private String justificationType; // "RETROACTIVA" | "ANTICIPADA"
}
