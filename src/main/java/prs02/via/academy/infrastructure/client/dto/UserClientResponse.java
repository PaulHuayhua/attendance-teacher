package prs02.via.academy.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserClientResponse {

    private UUID id;

    // Endpoint /users/teacher devuelve "idPerson", fallback a "id"
    @JsonProperty("idPerson")
    @Getter(lombok.AccessLevel.NONE)
    private UUID personId;

    public UUID getPersonId() {
        return personId != null ? personId : id;
    }

    @JsonProperty("adminId")
    private UUID adminId;

    // Endpoint /users/teacher devuelve "name" en lugar de "firstName"
    @JsonProperty("name")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("fullName")
    private String fullName;

    @JsonProperty("typeDocument")
    private String typeDocument;

    @JsonProperty("numberDocument")
    private String numberDocument;

    @JsonProperty("birthDate")
    private LocalDate birthDate;

    @JsonProperty("cellphone")
    private String cellphone;

    @JsonProperty("profileUrl")
    private String profileUrl;

    @JsonProperty("email")
    private String email;

    @JsonProperty("role")
    private String role;

    @JsonProperty("accessLevel")
    private String accessLevel;

    @JsonProperty("status")
    private String status;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    @JsonProperty("workstationCode")
    private String workstationCode;

    @JsonProperty("signatureUrl")
    private String signatureUrl;

    @JsonProperty("relationshipIds")
    private List<UUID> relationshipIds;

    @JsonProperty("idStudent")
    private UUID idStudent;

    @JsonProperty("idFamily")
    private UUID idFamily;

    @JsonProperty("idTeacher")
    private UUID idTeacher;

    @JsonProperty("courses")
    private List<CourseInfo> courses;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseInfo {
        @JsonProperty("courseCode")
        private String courseCode;

        @JsonProperty("courseName")
        private String courseName;
    }
}
