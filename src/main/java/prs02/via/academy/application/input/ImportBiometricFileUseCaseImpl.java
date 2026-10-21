package prs02.via.academy.application.input;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.application.output.BiometricMarkRepository;
import prs02.via.academy.domain.model.ImportResult;
import prs02.via.academy.domain.model.MarcacionBiometrica;
import prs02.via.academy.infrastructure.adapters.input.dto.BiometricFileRecord;
import prs02.via.academy.infrastructure.client.UsersClient;
import prs02.via.academy.infrastructure.client.dto.UserClientResponse;
import prs02.via.academy.infrastructure.utils.TenantContext;
import prs02.via.academy.infrastructure.utils.BiometricFileParser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementación del caso de uso de importación biométrica desde USB.
 *
 * Flujo:
 *  1. Parsear el archivo con {@link BiometricFileParser}
 *  2. Resolver cada PIN a un UUID de persona consultando el MS de usuarios
 *     (búsqueda por documento / workstationCode)
 *  3. Guardar cada marcación vía {@link BiometricMarkRepository} (procesado=false)
 *  4. Retornar un {@link ImportResult} con el resumen
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportBiometricFileUseCaseImpl implements ImportBiometricFileUseCase {

    private final BiometricFileParser parser;
    private final BiometricMarkRepository biometricMarkRepository;
    private final UsersClient usersClient;

    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024; // 10 MB

    @Override
    public Mono<ImportResult> importFile(byte[] content, String filename) {
        return TenantContext.get()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Falta contexto de tenant para importar marcaciones")))
                .flatMap(tenantId -> importFileForTenant(tenantId, content, filename));
    }

    private Mono<ImportResult> importFileForTenant(UUID tenantId, byte[] content, String filename) {
        log.info("Iniciando importación de archivo biométrico: {}, tamaño: {} bytes", filename, content.length);

        if (content.length > MAX_FILE_BYTES) {
            return Mono.error(new IllegalArgumentException(
                    "El archivo supera el tamaño máximo permitido (10 MB)"));
        }

        // 1. Parsear el archivo
        BiometricFileParser.ParseResult parsed = parser.parse(content, filename);
        log.info("Archivo parseado: {} líneas totales, {} registros válidos, {} omitidos",
                parsed.totalLines(), parsed.records().size(), parsed.skipped());

        if (parsed.records().isEmpty()) {
            return Mono.just(ImportResult.builder()
                    .totalLines(parsed.totalLines())
                    .processed(0)
                    .skipped(parsed.skipped())
                    .errors(parsed.parseErrors().size())
                    .errorDetails(parsed.parseErrors())
                    .build());
        }

        AtomicInteger savedCount  = new AtomicInteger(0);
        AtomicInteger errorCount  = new AtomicInteger(0);
        List<String>  errorDetails = Collections.synchronizedList(new ArrayList<>(parsed.parseErrors()));

        return usersClient.getAllTeachers()
                        .collectList()
                        .flatMap(teachers -> {
                            Map<String, UUID> pinToPersonId = buildPinMap(teachers);
                            log.info("Mapa PIN→personId construido con {} entradas", pinToPersonId.size());
                            if (pinToPersonId.isEmpty()) {
                                log.warn("⚠ El mapa de PINs está VACÍO. Profesores recibidos: {}", teachers.size());
                                teachers.forEach(t -> log.warn("  Teacher: id={} personId={} doc=[{}] workCode=[{}] nombre=[{} {}]",
                                        t.getId(), t.getPersonId(),
                                        t.getNumberDocument(), t.getWorkstationCode(),
                                        t.getFirstName(), t.getLastName()));
                            } else {
                                log.info("PIns disponibles en el mapa: {}", pinToPersonId.keySet());
                            }

                            return Flux.fromIterable(parsed.records())
                                    .flatMap(record -> processRecord(tenantId, record, pinToPersonId, savedCount, errorCount, errorDetails))
                                    .then(Mono.fromCallable(() -> ImportResult.builder()
                                            .totalLines(parsed.totalLines())
                                            .processed(savedCount.get())
                                            .skipped(parsed.skipped())
                                            .errors(errorCount.get())
                                            .errorDetails(errorDetails.subList(0, Math.min(errorDetails.size(), 20)))
                                            .build()));
                        })
                .doOnSuccess(result -> log.info(
                        "Importación completada: {} procesados, {} omitidos, {} errores",
                        result.getProcessed(), result.getSkipped(), result.getErrors()))
                .onErrorMap(ex -> {
                    log.error("Error durante la importación: {}", ex.getMessage());
                    return ex;
                });
    }

    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Construye un mapa PIN → personId a partir de la lista de usuarios.
     * Busca coincidencias en numberDocument y workstationCode (código de empleado).
     */
    private Map<String, UUID> buildPinMap(List<UserClientResponse> teachers) {
        Map<String, UUID> map = new HashMap<>();
        for (UserClientResponse teacher : teachers) {
            if (teacher.getPersonId() == null) continue;

            // Por número de documento
            if (teacher.getNumberDocument() != null && !teacher.getNumberDocument().isBlank()) {
                String docNormalized = teacher.getNumberDocument().replaceFirst("^0+(?!$)", "").trim();
                map.put(docNormalized, teacher.getPersonId());
                // También con ceros originales por si el reloj los exporta
                map.put(teacher.getNumberDocument().trim(), teacher.getPersonId());
            }

            // Por código de estación de trabajo (workstationCode = código biométrico)
            if (teacher.getWorkstationCode() != null && !teacher.getWorkstationCode().isBlank()) {
                String code = teacher.getWorkstationCode().replaceFirst("^0+(?!$)", "").trim();
                map.put(code, teacher.getPersonId());
                map.put(teacher.getWorkstationCode().trim(), teacher.getPersonId());
            }
        }
        return map;
    }

    /**
     * Procesa un registro individual: resuelve el PIN, valida y guarda la marcación.
     */
    private Mono<Void> processRecord(
            UUID tenantId,
            BiometricFileRecord record,
            Map<String, UUID> pinToPersonId,
            AtomicInteger savedCount,
            AtomicInteger errorCount,
            List<String> errorDetails) {

        UUID personId = pinToPersonId.get(record.pin());

        if (personId == null) {
            log.warn("PIN '{}' no encontrado en el sistema", record.pin());
            if (errorDetails.size() < 20) {
                errorDetails.add("PIN no encontrado: " + record.pin()
                        + " (" + record.dateTime() + ")");
            }
            errorCount.incrementAndGet();
            return Mono.empty();
        }

        MarcacionBiometrica marca = MarcacionBiometrica.builder()
                .personaId(personId)
                .fechaHora(record.dateTime())
                .procesado(false)
                .registradoEn(LocalDateTime.now())
                .rawData("USB:" + record.pin() + "|" + record.dateTime())
                .build();

        marca.setIdTenant(tenantId);
        return biometricMarkRepository.save(tenantId, marca)
                .doOnSuccess(saved -> {
                    savedCount.incrementAndGet();
                    log.debug("Marcación guardada: persona={}, fechaHora={}", personId, record.dateTime());
                })
                .onErrorResume(ex -> {
                    String msg = "Error guardando PIN " + record.pin() + " (" + record.dateTime() + "): " + ex.getMessage();
                    log.warn(msg);
                    if (errorDetails.size() < 20) errorDetails.add(msg);
                    errorCount.incrementAndGet();
                    return Mono.empty();
                })
                .then();
    }
}
