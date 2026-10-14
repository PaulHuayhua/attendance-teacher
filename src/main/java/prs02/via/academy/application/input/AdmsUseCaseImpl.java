package prs02.via.academy.application.input;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.application.output.BiometricMarkRepository;
import prs02.via.academy.domain.model.DeviceHeartbeat;
import prs02.via.academy.domain.model.MarcacionBiometrica;
import prs02.via.academy.infrastructure.client.UsersClient;
import prs02.via.academy.infrastructure.client.dto.UserClientResponse;
import prs02.via.academy.infrastructure.utils.TenantContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdmsUseCaseImpl implements AdmsUseCase {

    private final BiometricMarkRepository biometricMarkRepository;
    private final UsersClient usersClient;

    /** Mapa en memoria: serialNumber → heartbeat. Suficiente para 1-N dispositivos. */
    private final Map<DeviceKey, DeviceHeartbeat> heartbeatMap = new ConcurrentHashMap<>();

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    );

    private <T> Mono<T> missingTenant() {
        return Mono.error(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Falta contexto de tenant para la operación biométrica"));
    }

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public Mono<DeviceHeartbeat> registerHeartbeat(String serialNumber, String ipAddress) {
        return TenantContext.get()
                .switchIfEmpty(missingTenant())
                .map(tenantId -> {
                    DeviceKey key = new DeviceKey(tenantId, serialNumber);
                    DeviceHeartbeat hb = heartbeatMap.compute(key, (deviceKey, existing) -> {
                        if (existing == null) {
                            log.info("[ADMS] Nuevo dispositivo registrado: SN={}, IP={}", serialNumber, ipAddress);
                            return DeviceHeartbeat.builder()
                                    .serialNumber(serialNumber)
                                    .ipAddress(ipAddress)
                                    .lastSeen(LocalDateTime.now())
                                    .totalMarksSent(0)
                                    .build();
                        }
                        existing.setIpAddress(ipAddress);
                        existing.setLastSeen(LocalDateTime.now());
                        log.debug("[ADMS] Heartbeat de SN={}", serialNumber);
                        return existing;
                    });
                    return hb;
                });
    }

    @Override
    public Mono<Integer> processAttLog(String serialNumber, String body) {
        return TenantContext.get()
                .switchIfEmpty(missingTenant())
                .flatMap(tenantId -> processAttLogForTenant(tenantId, serialNumber, body));
    }

    private Mono<Integer> processAttLogForTenant(UUID tenantId, String serialNumber, String body) {
        if (body == null || body.isBlank()) {
            return Mono.just(0);
        }

        log.info("[ADMS] Recibiendo ATTLOG de SN={}, {} bytes", serialNumber, body.length());

        // Parsear todas las líneas del cuerpo
        List<AttLogLine> lines = body.lines()
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .map(this::parseAttLogLine)
                .filter(l -> l != null)
                .toList();

        if (lines.isEmpty()) {
            log.warn("[ADMS] ATTLOG de SN={} sin líneas válidas", serialNumber);
            return Mono.just(0);
        }

        log.info("[ADMS] {} líneas válidas en ATTLOG de SN={}", lines.size(), serialNumber);

        // Resolver PINs → personIds
        return usersClient.getAllTeachers()
                .collectList()
                .flatMap(teachers -> {
                    Map<String, UUID> pinMap = buildPinMap(teachers);
                    log.info("[ADMS] Mapa PIN→personId construido con {} entradas para SN={}", pinMap.size(), serialNumber);
                    if (pinMap.isEmpty()) {
                        log.warn("[ADMS] ⚠ Mapa de PINs VACÍO. Profesores recibidos del ms-usuarios: {}", teachers.size());
                        teachers.forEach(t -> log.warn("[ADMS]   Teacher: personId={} doc=[{}] workCode=[{}] nombre=[{} {}]",
                                t.getPersonId(), t.getNumberDocument(), t.getWorkstationCode(),
                                t.getFirstName(), t.getLastName()));
                    } else {
                        log.info("[ADMS] PINs disponibles: {}", pinMap.keySet());
                    }

                    return Flux.fromIterable(lines)
                            .flatMap(line -> saveMark(tenantId, line, pinMap, serialNumber))
                            .reduce(0, Integer::sum)
                            .doOnSuccess(count -> {
                                // Actualizar contador en heartbeat
                                heartbeatMap.computeIfPresent(new DeviceKey(tenantId, serialNumber), (sn, hb) -> {
                                    hb.setTotalMarksSent(hb.getTotalMarksSent() + count);
                                    hb.setLastSeen(LocalDateTime.now());
                                    return hb;
                                });
                                log.info("[ADMS] {} marcaciones guardadas de SN={}", count, serialNumber);
                            });
                });
    }

    @Override
    public Mono<DeviceHeartbeat> getDeviceStatus(String serialNumber) {
        return TenantContext.get()
                .switchIfEmpty(missingTenant())
                .flatMap(tenantId -> Mono.justOrEmpty(heartbeatMap.get(new DeviceKey(tenantId, serialNumber))));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parsea una línea ATTLOG con formato:
     *   PIN  DateTime           Status  Verify  [WorkCode]  [Reserved]
     *   001  2026-09-17 08:03:00  0       1
     *
     * Los campos pueden estar separados por TAB o espacios múltiples.
     */
    private AttLogLine parseAttLogLine(String line) {
        // Normalizar: reemplazar tabulaciones por espacio, split por espacios múltiples
        String[] parts = line.split("[\t]+|[ ]{2,}");

        // Mínimo necesario: PIN + DateTime (que puede ser 2 tokens: fecha + hora)
        if (parts.length < 2) return null;

        String pin = parts[0].trim();

        // Intentar fecha en token[1] (formato con espacio ya unido) o unir [1]+[2]
        LocalDateTime dateTime = null;

        // Caso 1: parts[1] ya contiene "yyyy-MM-dd HH:mm:ss" (split por tab)
        dateTime = tryParse(parts[1].trim());

        // Caso 2: la fecha y hora están en dos tokens separados
        if (dateTime == null && parts.length >= 3) {
            dateTime = tryParse(parts[1].trim() + " " + parts[2].trim());
        }

        if (pin.isBlank() || dateTime == null) return null;

        // Fechas futuras se ignoran
        if (dateTime.isAfter(LocalDateTime.now().plusMinutes(5))) return null;

        return new AttLogLine(pin, dateTime);
    }

    private LocalDateTime tryParse(String raw) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(raw, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private Map<String, UUID> buildPinMap(List<UserClientResponse> teachers) {
        Map<String, UUID> map = new java.util.HashMap<>();
        for (UserClientResponse t : teachers) {
            if (t.getPersonId() == null) continue;
            if (t.getNumberDocument() != null && !t.getNumberDocument().isBlank()) {
                map.put(t.getNumberDocument().trim(), t.getPersonId());
                map.put(t.getNumberDocument().replaceFirst("^0+(?!$)", "").trim(), t.getPersonId());
            }
            if (t.getWorkstationCode() != null && !t.getWorkstationCode().isBlank()) {
                map.put(t.getWorkstationCode().trim(), t.getPersonId());
                map.put(t.getWorkstationCode().replaceFirst("^0+(?!$)", "").trim(), t.getPersonId());
            }
        }
        return map;
    }

    private Mono<Integer> saveMark(UUID tenantId, AttLogLine line, Map<String, UUID> pinMap, String serialNumber) {
        UUID personId = pinMap.get(line.pin());
        if (personId == null) {
            log.warn("[ADMS] PIN '{}' no encontrado en el sistema", line.pin());
            return Mono.just(0);
        }

        MarcacionBiometrica marca = MarcacionBiometrica.builder()
                .personaId(personId)
                .idTenant(tenantId)
                .fechaHora(line.dateTime())
                .procesado(false)
                .registradoEn(LocalDateTime.now())
                .rawData("ADMS:" + serialNumber + "|" + line.pin() + "|" + line.dateTime())
                .build();

        return biometricMarkRepository.save(tenantId, marca)
                .map(saved -> 1)
                .onErrorResume(ex -> {
                    log.warn("[ADMS] Error guardando marca PIN={}: {}", line.pin(), ex.getMessage());
                    return Mono.just(0);
                });
    }

    private record AttLogLine(String pin, LocalDateTime dateTime) {}

    private record DeviceKey(UUID tenantId, String serialNumber) {}
}
