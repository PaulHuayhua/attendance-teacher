package prs02.via.academy.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.domain.model.HorarioDetalleDTO;
import prs02.via.academy.infrastructure.client.dto.ScheduleClientResponse;
import prs02.via.academy.infrastructure.utils.TenantContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SchedulesClient {

    private final WebClient schedulesWebClient;
    private final ConcurrentHashMap<String, ScheduleClientResponse> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 300000;

    public SchedulesClient(WebClient schedulesWebClient) {
        this.schedulesWebClient = schedulesWebClient;
    }

    /**
     * Obtiene todos los horarios activos para una fecha dada.
     * Una sola llamada al ms de horarios — sin necesidad del ms de usuarios.
     * Filtra los que tienen un detalle para el día de la semana correspondiente.
     */
    public Flux<ScheduleClientResponse> getStaffWithScheduleForDate(LocalDate date) {
        String dayOfWeek = date.getDayOfWeek().name()
                .replace("MONDAY", "LUNES")
                .replace("TUESDAY", "MARTES")
                .replace("WEDNESDAY", "MIERCOLES")
                .replace("THURSDAY", "JUEVES")
                .replace("FRIDAY", "VIERNES")
                .replace("SATURDAY", "SABADO")
                .replace("SUNDAY", "DOMINGO");

        log.debug("Obteniendo personal con horario para fecha={} ({})", date, dayOfWeek);

        return schedulesWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/horarios")
                        .queryParam("page", 0)
                        .queryParam("size", 100)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(json -> {
                    JsonNode items = json.has("data") && json.get("data").has("items")
                            ? json.get("data").get("items")
                            : json.has("data") && json.get("data").isArray()
                            ? json.get("data") : null;

                    if (items == null || !items.isArray()) return Flux.empty();

                    return Flux.fromIterable(items)
                            .map(this::parseScheduleNode)
                            .filter(s -> s != null
                                    && s.getPersonId() != null
                                    && Boolean.TRUE.equals(s.getActive())
                                    // vigente para la fecha: fechaInicio <= date <= fechaFin (o sin fin)
                                    && (s.getStartDate() == null || !s.getStartDate().isAfter(date))
                                    && (s.getEndDate()   == null || !s.getEndDate().isBefore(date))
                                    // tiene detalle para ese día de la semana
                                    && s.getDetails() != null
                                    && s.getDetails().stream().anyMatch(d -> dayOfWeek.equals(d.getDayOfWeek())));
                })
                .onErrorResume(error -> {
                    log.warn("Error al obtener horarios activos para fecha {}: {}", date, error.getMessage());
                    return Flux.empty();
                });
    }

    private ScheduleClientResponse parseScheduleNode(JsonNode node) {
        try {
            ScheduleClientResponse s = new ScheduleClientResponse();
            if (node.has("id") && !node.get("id").isNull())
                s.setId(UUID.fromString(node.get("id").asText()));
            if (node.has("personaId") && !node.get("personaId").isNull())
                s.setPersonId(UUID.fromString(node.get("personaId").asText()));
            if (node.has("personName") && !node.get("personName").isNull())
                s.setPersonName(node.get("personName").asText());
            if (node.has("fechaInicio") && !node.get("fechaInicio").isNull())
                s.setStartDate(LocalDate.parse(node.get("fechaInicio").asText()));
            if (node.has("fechaFin") && !node.get("fechaFin").isNull())
                s.setEndDate(LocalDate.parse(node.get("fechaFin").asText()));
            s.setActive(node.has("vigente") && node.get("vigente").asBoolean(true));

            if (node.has("detalles") && node.get("detalles").isArray()) {
                var details = new java.util.ArrayList<ScheduleClientResponse.ScheduleDetailResponse>();
                for (var d : node.get("detalles")) {
                    var detail = new ScheduleClientResponse.ScheduleDetailResponse();
                    if (d.has("diaSemana"))  detail.setDayOfWeek(d.get("diaSemana").asText());
                    if (d.has("turno"))      detail.setTurno(d.get("turno").asText());
                    if (d.has("horaEntrada") && !d.get("horaEntrada").isNull())
                        detail.setEntryTime(java.time.LocalTime.parse(d.get("horaEntrada").asText()));
                    if (d.has("horaSalida") && !d.get("horaSalida").isNull())
                        detail.setExitTime(java.time.LocalTime.parse(d.get("horaSalida").asText()));
                    if (d.has("esLaborable"))
                        detail.setIsWorkday(d.get("esLaborable").asBoolean());
                    details.add(detail);
                }
                s.setDetails(details);
            }
            return s;
        } catch (Exception e) {
            log.warn("Error parseando horario: {}", e.getMessage());
            return null;
        }
    }

    public Mono<Set<UUID>> getTeachersWithSchedules() {
        log.debug("Obteniendo profesores con horarios registrados");

        return schedulesWebClient
                .get()
                .uri("/horarios")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .mapNotNull(json -> {
                    JsonNode dataArray = json.has("data") && json.get("data").isArray()
                            ? json.get("data") : null;
                    if (dataArray == null) return Set.<UUID>of();

                    Set<UUID> teacherIds = new java.util.LinkedHashSet<>();
                    for (var item : dataArray) {
                        if (item.has("personaId") && !item.get("personaId").isNull()) {
                            try {
                                teacherIds.add(UUID.fromString(item.get("personaId").asText()));
                            } catch (Exception e) {
                                log.warn("Error parseando personaId de horario: {}", e.getMessage());
                            }
                        }
                    }
                    return teacherIds;
                })
                .onErrorResume(error -> {
                    log.warn("Error al obtener profesores con horarios: {}", error.getMessage());
                    return Mono.just(Set.of());
                });
    }

    public Flux<HorarioDetalleDTO> getActiveScheduleDetails(UUID personaId, LocalDate date) {
        return TenantContext.get()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Falta contexto de tenant para consultar horarios")))
                .flatMapMany(tenantId -> getActiveScheduleDetailsForTenant(tenantId, personaId, date));
    }

    private Flux<HorarioDetalleDTO> getActiveScheduleDetailsForTenant(
            UUID tenantId, UUID personaId, LocalDate date) {
        String cacheKey = tenantId + "|horario_" + personaId + "_" + date;

        ScheduleClientResponse cached = cache.get(cacheKey);
        if (cached != null && cached.getDetails() != null) {
            log.debug("Usando horario cacheado para tenant: {}, persona: {}, fecha: {}", tenantId, personaId, date);
            return Flux.fromIterable(cached.getDetails())
                    .map(this::mapToHorarioDetalle);
        }

        String uri = "/horarios/persona/{personaId}/active?date={date}";
        log.debug("Consultando horario activo: {}", uri);

        return schedulesWebClient
                .get()
                .uri(uri, personaId, date.toString())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .mapNotNull(json -> json.has("data") && !json.get("data").isNull() ? json.get("data") : null)
                .flatMapMany(dataNode -> {
                    try {
                        ScheduleClientResponse schedule = parseScheduleResponse(dataNode);
                        if (schedule != null) {
                            cache.put(cacheKey, schedule);
                            log.debug("Horario cacheado para tenant: {}, persona: {}, fecha: {}", tenantId, personaId, date);
                            return Flux.fromIterable(schedule.getDetails() == null ? java.util.List.of() : schedule.getDetails())
                                    .map(this::mapToHorarioDetalle);
                        }
                        return Flux.empty();
                    } catch (Exception e) {
                        log.error("Error parseando respuesta de horarios: {}", e.getMessage());
                        return Flux.empty();
                    }
                })
                .doOnError(WebClientResponseException.class, error -> {
                    log.error("Error HTTP al consultar horarios: {} - {}", error.getStatusCode(), error.getResponseBodyAsString());
                    ScheduleClientResponse fallback = cache.get(cacheKey);
                    if (fallback != null) {
                        log.warn("Usando dato cacheado como fallback para tenant: {}, persona: {}, fecha: {}", tenantId, personaId, date);
                    }
                })
                .onErrorResume(error -> {
                    log.warn("Error consultando horarios, usando caché si está disponible: {}", error.getMessage());
                    ScheduleClientResponse fallback = cache.get(cacheKey);
                    if (fallback != null && fallback.getDetails() != null) {
                        return Flux.fromIterable(fallback.getDetails())
                                .map(this::mapToHorarioDetalle);
                    }
                    return Flux.empty();
                });
    }

    private HorarioDetalleDTO mapToHorarioDetalle(ScheduleClientResponse.ScheduleDetailResponse detail) {
        return HorarioDetalleDTO.builder()
                .diaSemana(detail.getDayOfWeek())
                .turno(detail.getTurno())
                .horaEntrada(detail.getEntryTime())
                .horaSalida(detail.getExitTime())
                .esLaborable(detail.getIsWorkday())
                .build();
    }

    private ScheduleClientResponse parseScheduleResponse(JsonNode dataNode) {
        try {
            ScheduleClientResponse schedule = new ScheduleClientResponse();
            if (dataNode.has("id") && !dataNode.get("id").isNull()) {
                schedule.setId(UUID.fromString(dataNode.get("id").asText()));
            }
            if (dataNode.has("personaId") && !dataNode.get("personaId").isNull()) {
                schedule.setPersonId(UUID.fromString(dataNode.get("personaId").asText()));
            }

            var detailsNode = dataNode.get("detalles");
            if (detailsNode != null && detailsNode.isArray()) {
                var details = new java.util.ArrayList<ScheduleClientResponse.ScheduleDetailResponse>();
                for (var d : detailsNode) {
                    var detail = new ScheduleClientResponse.ScheduleDetailResponse();
                    if (d.has("id") && !d.get("id").isNull()) {
                        detail.setId(UUID.fromString(d.get("id").asText()));
                    }
                    if (d.has("diaSemana") && !d.get("diaSemana").isNull()) {
                        detail.setDayOfWeek(d.get("diaSemana").asText());
                    }
                    if (d.has("turno") && !d.get("turno").isNull()) {
                        detail.setTurno(d.get("turno").asText());
                    }
                    if (d.has("horaEntrada") && !d.get("horaEntrada").isNull()) {
                        detail.setEntryTime(java.time.LocalTime.parse(d.get("horaEntrada").asText()));
                    }
                    if (d.has("horaSalida") && !d.get("horaSalida").isNull()) {
                        detail.setExitTime(java.time.LocalTime.parse(d.get("horaSalida").asText()));
                    }
                    if (d.has("esLaborable") && !d.get("esLaborable").isNull()) {
                        detail.setIsWorkday(d.get("esLaborable").asBoolean());
                    }
                    details.add(detail);
                }
                schedule.setDetails(details);
            }
            return schedule;
        } catch (Exception e) {
            log.error("Error parseando respuesta de horarios: {}", e.getMessage());
            return null;
        }
    }
}
