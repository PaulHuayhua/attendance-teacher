package prs02.via.academy.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * DTO de respuesta del MS de justificaciones (ProxyJustification).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JustificationResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("staffName")
    private String staffName;

    @JsonProperty("staffLastName")
    private String staffLastName;

    @JsonProperty("approvalStatus")
    private String approvalStatus; // null, "PENDING", "APPROVED", "DENIED"

    @JsonProperty("justificationType")
    private String justificationType;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("specificReason")
    private String specificReason;

    @JsonProperty("startDate")
    private String startDate;

    @JsonProperty("endDate")
    private String endDate;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("creationDate")
    private String creationDate;
}
