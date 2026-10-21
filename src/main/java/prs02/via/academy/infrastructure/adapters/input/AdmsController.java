package prs02.via.academy.infrastructure.adapters.input;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import prs02.via.academy.application.input.AdmsUseCase;
import prs02.via.academy.application.input.ProcessAttendanceUseCase;
import prs02.via.academy.domain.model.DeviceHeartbeat;
import prs02.via.academy.infrastructure.adapters.input.dto.ApiResponseDTO;
import prs02.via.academy.infrastructure.utils.TenantContext;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints del protocolo ZKTeco PUSH / ADMS.
 *
 * El dispositivo WL30 debe ser configurado con:
 *   Server Address: http://<tu-servidor>/iclock
 *   Port: 80 (o el que uses)
 *
 * Flujo automático del dispositivo:
 *   1. GET  /iclock/cdata?SN=XXX&options=all      → registro + respuesta de config
 *   2. POST /iclock/cdata?SN=XXX&table=ATTLOG      → envío de marcaciones
 *   3. GET  /iclock/getrequest?SN=XXX              → heartbeat periódico
 *
 * Todos los endpoints devuelven texto plano porque eso es lo que el firmware espera.
 */
@RestController
@RequestMapping("/api/v1/iclock")
@RequiredArgsConstructor
@Slf4j
public class AdmsController {

    private final AdmsUseCase admsUseCase;
    private final ProcessAttendanceUseCase processAttendanceUseCase;

    // ── 1. Registro del dispositivo + respuesta de configuración ───────────────
    /**
     * El WL30 llama a este endpoint al encender o reconectarse.
     * Respuesta esperada: texto plano con los parámetros de configuración.
     */
    @GetMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<ResponseEntity<String>> deviceRegister(
            @RequestParam("SN") String serialNumber,
            @RequestParam(value = "options", required = false) String options,
            @RequestHeader(value = "X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Real-IP", required = false) String realIp,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor) {

        String ip = resolveIp(realIp, forwardedFor, "unknown");
        log.info("[ADMS] Registro/check-in de dispositivo SN={} desde IP={}", serialNumber, ip);

        return admsUseCase.registerHeartbeat(serialNumber, ip)
                .map(hb -> {
                    String config = String.join("\r\n",
                            "GET OPTION FROM: " + serialNumber,
                            "ATTLOGStamp=9999",
                            "OPERLOGStamp=9999",
                            "ATTPHOTOStamp=0",
                            "ErrorDelay=30",
                            "Delay=10",
                            "TransTimes=00:00;14:05",
                            "TransInterval=1",
                            "TransFlag=TransData AttLog OpLog EnrollUser ChgUser",
                            "TimeZone=0",
                            "Realtime=1",
                            "Encrypt=None"
                    );
                    return ResponseEntity.ok(config);
                })
                .contextWrite(ctx -> ctx.put(TenantContext.KEY, tenantId));
    }

    // ── 2. Recepción de marcaciones (ATTLOG) ───────────────────────────────────
    /**
     * El WL30 envía las marcaciones como texto plano en el body.
     * Formato de cada línea:
     *   PIN<TAB>DateTime<TAB>Status<TAB>Verify<TAB>WorkCode<TAB>Reserved
     *   001<TAB>2026-09-17 08:03:00<TAB>0<TAB>1<TAB>0<TAB>0
     */
    @PostMapping(value = "/cdata",
                 consumes = MediaType.TEXT_PLAIN_VALUE,
                 produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<ResponseEntity<String>> receiveAttLog(
            @RequestParam("SN") String serialNumber,
            @RequestParam(value = "table", required = false, defaultValue = "") String table,
            @RequestBody String body,
            @RequestHeader(value = "X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Real-IP", required = false) String realIp,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor) {

        String ip = resolveIp(realIp, forwardedFor, "unknown");
        log.info("[ADMS] POST /cdata SN={} table={} desde IP={}", serialNumber, table, ip);

        return admsUseCase.registerHeartbeat(serialNumber, ip)
                .then("ATTLOG".equalsIgnoreCase(table)
                        ? admsUseCase.processAttLog(serialNumber, body)
                        : Mono.just(0))
                .flatMap(count -> count > 0
                        ? processAttendanceUseCase.processAllPending().then(Mono.just(count))
                        : Mono.just(count))
                .map(count -> ResponseEntity.ok("OK"))
                .onErrorResume(err -> {
                    log.error("[ADMS] Error procesando ATTLOG de SN={}: {}", serialNumber, err.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR"));
                })
                .contextWrite(ctx -> ctx.put(TenantContext.KEY, tenantId));
    }

    // ── 3. Heartbeat periódico ─────────────────────────────────────────────────
    /**
     * El dispositivo llama a este endpoint cada N segundos (configurado con Delay=10).
     * El servidor puede responder con comandos para el dispositivo (aquí respondemos vacío).
     */
    @GetMapping(value = "/getrequest", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<ResponseEntity<String>> heartbeat(
            @RequestParam("SN") String serialNumber,
            @RequestHeader(value = "X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Real-IP", required = false) String realIp,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor) {

        String ip = resolveIp(realIp, forwardedFor, "unknown");
        log.debug("[ADMS] Heartbeat de SN={} desde IP={}", serialNumber, ip);

        return admsUseCase.registerHeartbeat(serialNumber, ip)
                .map(hb -> ResponseEntity.ok("OK"))
                .contextWrite(ctx -> ctx.put(TenantContext.KEY, tenantId));
    }

    // ── API interna: estado del dispositivo (para el frontend) ─────────────────
    /**
     * El frontend Angular llama a este endpoint para mostrar el estado
     * del dispositivo en el panel "Dispositivo ZKTeco".
     *
     * GET /iclock/status?SN=XXX
     */
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponseDTO<DeviceHeartbeat>>> getStatus(
            @RequestParam(value = "SN", required = false) String serialNumber,
            @RequestHeader(value = "X-Tenant-Id") UUID tenantId) {

        if (serialNumber == null || serialNumber.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ApiResponseDTO.<DeviceHeartbeat>builder()
                            .message("Parámetro SN requerido")
                            .data(null)
                            .build()));
        }

        return admsUseCase.getDeviceStatus(serialNumber)
                .map(hb -> ResponseEntity.ok(
                        ApiResponseDTO.ok("Estado del dispositivo obtenido", hb)))
                .defaultIfEmpty(ResponseEntity.ok(
                        ApiResponseDTO.<DeviceHeartbeat>builder()
                                .message("Dispositivo no registrado aún. Verifica que esté encendido y configurado con la URL del servidor.")
                                .data(null)
                                .build()))
                .contextWrite(ctx -> ctx.put(TenantContext.KEY, tenantId));
    }

    // ── API interna: listado de todos los dispositivos ─────────────────────────
    /**
     * GET /iclock/devices
     * Devuelve todos los dispositivos que han contactado al servidor.
     */
    @GetMapping(value = "/devices", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponseDTO<Map<String, Object>>>> getDevices() {
        // El estado se maneja en memoria en AdmsUseCaseImpl
        // Este endpoint puede expandirse con persistencia en el futuro
        return Mono.just(ResponseEntity.ok(
                ApiResponseDTO.ok("Endpoint disponible. Usa /iclock/status?SN=<serial> para consultar un dispositivo.",
                        Map.of("info", "Consulta /iclock/status?SN=<serial> para estado de un dispositivo específico",
                               "timestamp", LocalDateTime.now().toString()))));
    }

    // ── Helper ─────────────────────────────────────────────────────────────────
    private String resolveIp(String realIp, String forwardedFor, String fallback) {
        if (realIp != null && !realIp.isBlank()) return realIp;
        if (forwardedFor != null && !forwardedFor.isBlank()) return forwardedFor.split(",")[0].trim();
        return fallback;
    }
}
