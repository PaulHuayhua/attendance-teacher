package prs02.via.academy.infrastructure.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.infrastructure.client.dto.JustificationRequest;
import prs02.via.academy.infrastructure.client.dto.JustificationResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JustificationsClient {

    @Qualifier("justificationsWebClient")
    private final WebClient justificationsWebClient;

    private final ObjectMapper objectMapper;

    /**
     * Crea una justificación en el MS de justificaciones via POST /proxyJustifications/new
     * El MS espera multipart/form-data con el campo "justification" como JSON blob,
     * y los archivos "documents" y "signature" (opcionales desde el backend).
     */
    public Mono<JustificationResponse> createJustification(JustificationRequest request) {
        log.info("Enviando justificación al MS de justificaciones para: {} {}",
                request.getStaffName(), request.getStaffLastName());

        return Mono.defer(() -> {
            String justificationJson;
            try {
                justificationJson = objectMapper.writeValueAsString(request);
            } catch (JsonProcessingException e) {
                return Mono.<JustificationResponse>error(
                        new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                "Error al serializar justificación"));
            }

            // El MS espera multipart con "justification" como Blob JSON
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("justification",
                    new ByteArrayResource(justificationJson.getBytes()) {
                        @Override
                        public String getFilename() { return "justification.json"; }
                    })
                    .contentType(MediaType.APPLICATION_JSON);

            // Documento vacío requerido por el endpoint
            builder.part("documents",
                    new ByteArrayResource(new byte[0]) {
                        @Override
                        public String getFilename() { return "empty.pdf"; }
                    })
                    .contentType(MediaType.APPLICATION_OCTET_STREAM);

            // Firma vacía requerida por el endpoint
            builder.part("signature",
                    new ByteArrayResource(new byte[0]) {
                        @Override
                        public String getFilename() { return "signature.png"; }
                    })
                    .contentType(MediaType.APPLICATION_OCTET_STREAM);

            return justificationsWebClient
                    .post()
                    .uri("/proxyJustifications/new")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> response.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Error 4xx al crear justificación: {}", errorBody);
                                        return Mono.error(new ResponseStatusException(
                                                response.statusCode(),
                                                "Error al crear justificación: " + errorBody));
                                    })
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> {
                                log.error("Error 5xx del MS de justificaciones");
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.SERVICE_UNAVAILABLE,
                                        "El MS de justificaciones no está disponible"));
                            }
                    )
                    .bodyToMono(JustificationResponse.class)
                    .doOnSuccess(resp -> log.info("Justificación creada en MS externo con ID: {}", resp.getId()))
                    .doOnError(error -> log.error("Error al crear justificación: {}", error.getMessage()));
        });
    }

    /**
     * Consulta todas las justificaciones y filtra por staffName para encontrar
     * las relacionadas a un profesor (ya que no hay endpoint por attendanceRecordId).
     */
    public Mono<JustificationResponse> getJustificationByAttendanceRecordId(UUID attendanceRecordId) {
        log.info("Consultando justificaciones en MS externo para registro: {}", attendanceRecordId);

        return Mono.defer(() -> justificationsWebClient
                .get()
                .uri("/proxyJustifications")
                .retrieve()
                    .bodyToFlux(JustificationResponse.class)
                    .filter(r -> r.getSpecificReason() != null
                            && r.getSpecificReason().contains(attendanceRecordId.toString()))
                    .next()
                    .switchIfEmpty(Mono.error(new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "No se encontró justificación para el registro: " + attendanceRecordId)))
                    .doOnSuccess(resp -> log.info("Justificación encontrada con estado: {}", resp.getApprovalStatus()))
                    .doOnError(error -> log.warn("Error al consultar justificación: {}", error.getMessage())));
    }
}
