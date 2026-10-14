package prs02.via.academy.infrastructure.adapters.input;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import prs02.via.academy.application.input.*;
import prs02.via.academy.domain.model.HorarioDetalleDTO;
import prs02.via.academy.domain.model.RegistroAsistencia;
import prs02.via.academy.infrastructure.adapters.input.dto.*;
import prs02.via.academy.infrastructure.adapters.input.mapper.AttendanceControllerMapper;
import prs02.via.academy.infrastructure.client.SchedulesClient;
import prs02.via.academy.infrastructure.client.UsersClient;
import prs02.via.academy.infrastructure.utils.ExcelReportGenerator;
import prs02.via.academy.infrastructure.utils.TenantContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Slf4j
public class AttendanceController {

    private final GetAttendanceUseCase getAttendanceUseCase;
    private final ProcessAttendanceUseCase processAttendanceUseCase;
    private final JustifyAttendanceUseCase justifyAttendanceUseCase;
    private final ReceiveMarkUseCase receiveMarkUseCase;
    private final ManualRegisterUseCase manualRegisterUseCase;
    private final ImportBiometricFileUseCase importBiometricFileUseCase;
    private final UsersClient usersClient;
    private final SchedulesClient schedulesClient;
    private final ExcelReportGenerator excelReportGenerator;
    private final prs02.via.academy.application.output.JustificationPort justificationPort;

    @GetMapping
    public Mono<ResponseEntity<ApiResponseDTO<List<AttendanceControllerResponse>>>> findAll() {
        return TenantContext.withTenant(tenantId ->
                getAttendanceUseCase.findAll(tenantId)
                        .flatMap(this::enrichWithUser)
                        .collectList()
                        .map(list -> ResponseEntity.ok(
                                ApiResponseDTO.ok("Asistencias obtenidas correctamente", list)))
        );
    }

    @GetMapping("/date/{fecha}")
    public Mono<ResponseEntity<ApiResponseDTO<List<AttendanceControllerResponse>>>> findByDate(
            @PathVariable String fecha) {
        LocalDate targetDate = parseDate(fecha);
        if (targetDate == null) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ApiResponseDTO.<List<AttendanceControllerResponse>>builder()
                            .message("Formato de fecha inválido. Use yyyy-MM-dd")
                            .data(null)
                            .build()));
        }

        return TenantContext.withTenant(tenantId ->
                getAttendanceUseCase.findByFecha(tenantId, targetDate)
                        .flatMap(this::enrichWithUser)
                        .collectList()
                        .map(list -> ResponseEntity.ok(
                                ApiResponseDTO.ok("Asistencias de la fecha obtenidas", list)))
        );
    }

    @GetMapping("/range")
    public Mono<ResponseEntity<ApiResponseDTO<List<AttendanceControllerResponse>>>> findByDateRange(
            @RequestParam String from,
            @RequestParam String to) {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);

        if (fromDate == null || toDate == null) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ApiResponseDTO.<List<AttendanceControllerResponse>>builder()
                            .message("Formato de fecha inválido. Use yyyy-MM-dd")
                            .data(null)
                            .build()));
        }

        return TenantContext.withTenant(tenantId ->
                getAttendanceUseCase.findByFechaBetween(tenantId, fromDate, toDate)
                        .flatMap(this::enrichWithUser)
                        .collectList()
                        .map(list -> ResponseEntity.ok(
                                ApiResponseDTO.ok("Asistencias del rango obtenidas", list)))
        );
    }

    @GetMapping("/pending-justification")
    public Mono<ResponseEntity<ApiResponseDTO<List<AttendanceControllerResponse>>>> getPendingJustification(
            @RequestParam String from,
            @RequestParam String to) {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);

        if (fromDate == null || toDate == null) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ApiResponseDTO.<List<AttendanceControllerResponse>>builder()
                            .message("Formato de fecha inválido. Use yyyy-MM-dd")
                            .data(null)
                            .build()));
        }

        return TenantContext.withTenant(tenantId ->
                getAttendanceUseCase.findByFechaBetween(tenantId, fromDate, toDate)
                        .filter(r -> "TARDE".equals(r.getEstado()) || "FALTA".equals(r.getEstado()))
                        .flatMap(this::enrichWithUser)
                        .collectList()
                        .map(list -> ResponseEntity.ok(
                                ApiResponseDTO.ok("Registros pendientes de justificación obtenidos", list)))
        );
    }

    @GetMapping("/teacher/{personaId}")
    public Mono<ResponseEntity<ApiResponseDTO<List<AttendanceControllerResponse>>>> findByTeacher(
            @PathVariable UUID personaId) {
        return TenantContext.withTenant(tenantId ->
                getAttendanceUseCase.findByPersonaId(tenantId, personaId)
                        .flatMap(this::enrichWithUser)
                        .collectList()
                        .map(list -> ResponseEntity.ok(
                                ApiResponseDTO.ok("Asistencias del profesor obtenidas", list)))
        );
    }

    @GetMapping("/teacher/{personaId}/history")
    public Mono<ResponseEntity<ApiResponseDTO<List<AttendanceControllerResponse>>>> findHistory(
            @PathVariable UUID personaId,
            @RequestParam String from,
            @RequestParam String to) {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);

        if (fromDate == null || toDate == null) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ApiResponseDTO.<List<AttendanceControllerResponse>>builder()
                            .message("Formato de fecha inválido. Use yyyy-MM-dd")
                            .data(null)
                            .build()));
        }

        return TenantContext.withTenant(tenantId ->
                getAttendanceUseCase.findHistoryByPersonaId(tenantId, personaId, fromDate, toDate)
                        .flatMap(this::enrichWithUser)
                        .collectList()
                        .map(list -> ResponseEntity.ok(
                                ApiResponseDTO.ok("Historial del profesor obtenido", list)))
        );
    }

    @GetMapping("/summary")
    public Mono<ResponseEntity<ApiResponseDTO<Map<String, Object>>>> getSummary(
            @RequestParam(required = false) String date) {
        LocalDate targetDate = date != null ? parseDate(date) : LocalDate.now();

        if (date != null && targetDate == null) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ApiResponseDTO.<Map<String, Object>>builder()
                            .message("Formato de fecha inválido. Use yyyy-MM-dd")
                            .data(null)
                            .build()));
        }

        return TenantContext.withTenant(tenantId ->
                getAttendanceUseCase.getSummary(tenantId, targetDate)
                        .map(summary -> ResponseEntity.ok(
                                ApiResponseDTO.ok("Resumen del día obtenido", summary)))
        );
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponseDTO<AttendanceControllerResponse>>> findById(@PathVariable UUID id) {
        return TenantContext.withTenant(tenantId ->
                getAttendanceUseCase.findById(tenantId, id)
                        .flatMap(this::enrichWithUser)
                        .map(dto -> ResponseEntity.ok(
                                ApiResponseDTO.ok("Registro de asistencia encontrado", dto)))
        );
    }

    @GetMapping("/teachers")
    public Mono<ResponseEntity<ApiResponseDTO<List<TeacherResponseDTO>>>> getTeachers(
            @RequestParam(required = false) String date) {
        LocalDate targetDate = (date != null && !date.isBlank()) ? parseDate(date) : LocalDate.now();
        if (targetDate == null) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ApiResponseDTO.<List<TeacherResponseDTO>>builder()
                            .message("Formato de fecha inválido. Use yyyy-MM-dd")
                            .data(null)
                            .build()));
        }

        return TenantContext.withTenant(tenantId -> schedulesClient.getStaffWithScheduleForDate(targetDate)
                .map(schedule -> {
                    String fullName = schedule.getPersonName() != null
                            ? schedule.getPersonName()
                            : schedule.getPersonId() != null ? schedule.getPersonId().toString() : "Desconocido";
                    return TeacherResponseDTO.builder()
                            .personId(schedule.getPersonId())
                            .fullName(fullName)
                            .build();
                })
                .collectList()
                .map(all -> {
                    java.util.Map<UUID, TeacherResponseDTO> dedup = new java.util.LinkedHashMap<>();
                    all.forEach(t -> dedup.putIfAbsent(t.getPersonId(), t));
                    List<TeacherResponseDTO> result = new java.util.ArrayList<>(dedup.values());
                    return result;
                })
                .map(teachers -> ResponseEntity.ok(
                        ApiResponseDTO.ok("Personal con horario obtenido correctamente", teachers))));
    }

    @GetMapping("/teachers/{personaId}/schedule")
    public Mono<ResponseEntity<ApiResponseDTO<List<TeacherScheduleResponse>>>> getTeacherSchedule(
            @PathVariable UUID personaId,
            @RequestParam(required = false) String date) {
        LocalDate targetDate = date != null ? parseDate(date) : LocalDate.now();
        if (date != null && targetDate == null) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ApiResponseDTO.<List<TeacherScheduleResponse>>builder()
                            .message("Formato de fecha inválido. Use yyyy-MM-dd")
                            .data(null)
                            .build()));
        }

        return TenantContext.withTenant(tenantId -> schedulesClient.getActiveScheduleDetails(personaId, targetDate)
                .map(detail -> TeacherScheduleResponse.builder()
                        .diaSemana(detail.getDiaSemana())
                        .turno(detail.getTurno())
                        .horaEntrada(detail.getHoraEntrada())
                        .horaSalida(detail.getHoraSalida())
                        .build())
                .collectList()
                .map(list -> ResponseEntity.ok(
                        ApiResponseDTO.ok("Horario del profesor obtenido", list))));
    }

    @PostMapping("/mark")
    public Mono<ResponseEntity<ApiResponseDTO<String>>> receiveMark(
            @Valid @RequestBody MarkRequest request) {
        log.info("Recibiendo marcación biométrica: personaId={}, fechaHora={}",
                request.getPersonaId(), request.getFechaHora());

        return TenantContext.withTenant(tenantId -> receiveMarkUseCase.receiveMark(AttendanceControllerMapper.toDomain(request))
                .map(saved -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(ApiResponseDTO.ok("Marcación recibida correctamente", saved.getId().toString())))
                .doOnError(error -> log.error("Error al recibir marcación: ", error)));
    }

    @GetMapping("/process-pending")
    public Mono<ResponseEntity<ApiResponseDTO<Map<String, Object>>>> processPending() {
        log.info("Procesamiento manual de marcaciones pendientes iniciado desde el frontend");

        return TenantContext.withTenant(tenantId -> processAttendanceUseCase.processAllPending()
                .collectList()
                .map(records -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("processed", records.size());
                    result.put("message", records.size() + " registros de asistencia generados");
                    return ResponseEntity.ok(
                            ApiResponseDTO.ok("Procesamiento completado", result));
                }));
    }

    @PostMapping("/manual")
    public Mono<ResponseEntity<ApiResponseDTO<AttendanceControllerResponse>>> manualRegister(
            @Valid @RequestBody ManualRegisterRequest request) {
        log.info("Registro manual de asistencia: personaId={}, fecha={}, turno={}",
                request.getPersonaId(), request.getFecha(), request.getTurno());

        return TenantContext.withTenant(tenantId -> {
            RegistroAsistencia record = AttendanceControllerMapper.toDomain(request);
            record.setIdTenant(tenantId);
            return manualRegisterUseCase.register(record)
                    .flatMap(this::enrichWithUser)
                    .map(saved -> ResponseEntity
                            .status(HttpStatus.CREATED)
                            .body(ApiResponseDTO.ok("Asistencia registrada manualmente", saved)))
                    .doOnError(error -> log.error("Error al registrar asistencia manual: ", error));
        });
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ApiResponseDTO<prs02.via.academy.domain.model.ImportResult>>> importBiometricFile(
            @RequestPart("file") FilePart filePart) {

        log.info("Recibiendo archivo biométrico para importar: {}", filePart.filename());

        return TenantContext.withTenant(tenantId -> filePart.content()
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    org.springframework.core.io.buffer.DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .reduce(new byte[0], (acc, chunk) -> {
                    byte[] merged = new byte[acc.length + chunk.length];
                    System.arraycopy(acc, 0, merged, 0, acc.length);
                    System.arraycopy(chunk, 0, merged, acc.length, chunk.length);
                    return merged;
                })
                .flatMap(bytes -> importBiometricFileUseCase.importFile(bytes, filePart.filename()))
                .map(result -> ResponseEntity.ok(
                        ApiResponseDTO.ok(
                                "Importación completada: " + result.getProcessed() + " marcaciones procesadas",
                                result)))
                .onErrorResume(ex -> {
                    log.error("Error en importación de archivo biométrico: {}", ex.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(ApiResponseDTO.<prs02.via.academy.domain.model.ImportResult>builder()
                                    .message("Error al procesar el archivo: " + ex.getMessage())
                                    .data(null)
                                    .build()));
                }));
    }

    @GetMapping("/export")
    public Mono<ResponseEntity<byte[]>> exportExcel(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        List<YearMonth> months;

        if (month != null) {
            months = List.of(YearMonth.parse(month));
        } else if (from != null && to != null) {
            YearMonth fromMonth = YearMonth.from(LocalDate.parse(from));
            YearMonth toMonth = YearMonth.from(LocalDate.parse(to));
            months = new ArrayList<>();
            YearMonth current = fromMonth;
            while (!current.isAfter(toMonth)) {
                months.add(current);
                current = current.plusMonths(1);
            }
        } else {
            YearMonth currentMonth = YearMonth.now();
            months = List.of(currentMonth);
        }

        Map<YearMonth, List<AttendanceControllerResponse>> dataByMonth = new LinkedHashMap<>();

        return TenantContext.withTenant(tenantId ->
                Flux.fromIterable(months)
                        .flatMap(ym -> {
                            LocalDate start = ym.atDay(1);
                            LocalDate end = ym.atEndOfMonth();
                            return getAttendanceUseCase.findByFechaBetween(tenantId, start, end)
                                    .flatMap(this::enrichWithUser)
                                    .collectList()
                                    .map(records -> Map.entry(ym, records));
                        })
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                        .flatMap(map -> {
                            Set<UUID> teacherIds = map.values().stream()
                                    .flatMap(Collection::stream)
                                    .map(AttendanceControllerResponse::getPersonaId)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet());

                            return Flux.fromIterable(teacherIds)
                                    .flatMap(pid -> schedulesClient.getActiveScheduleDetails(pid, LocalDate.now())
                                            .collectList()
                                            .map(details -> Map.entry(pid, details))
                                            .defaultIfEmpty(Map.entry(pid, List.of())))
                                    .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                                    .flatMap(scheduleMap -> {
                                        map.values().forEach(records ->
                                            records.forEach(r -> {
                                                if ((r.getMinutosTrabajados() == null || r.getMinutosTrabajados() == 0)
                                                        && r.getPersonaId() != null
                                                        && ("PRESENTE".equals(r.getEstado()) || "TARDE".equals(r.getEstado()))) {
                                                    List<HorarioDetalleDTO> details = scheduleMap.get(r.getPersonaId());
                                                    if (details != null) {
                                                        details.stream()
                                                                .filter(d -> d.getTurno() != null && d.getTurno().equalsIgnoreCase(r.getTurno()))
                                                                .findFirst()
                                                                .ifPresent(d -> {
                                                                    if (d.getHoraEntrada() != null && d.getHoraSalida() != null) {
                                                                        int expected = (int) java.time.Duration.between(d.getHoraEntrada(), d.getHoraSalida()).toMinutes();
                                                                        r.setMinutosTrabajados(expected);
                                                                    }
                                                                });
                                                    }
                                                }
                                            })
                                        );

                                        Map<UUID, Integer> expectedMap = scheduleMap.entrySet().stream()
                                                .collect(Collectors.toMap(
                                                        Map.Entry::getKey,
                                                        e -> {
                                                            int total = e.getValue().stream()
                                                                    .filter(d -> Boolean.TRUE.equals(d.getEsLaborable()))
                                                                    .mapToInt(d -> d.getHoraEntrada() != null && d.getHoraSalida() != null
                                                                            ? (int) java.time.Duration.between(d.getHoraEntrada(), d.getHoraSalida()).toMinutes()
                                                                            : 0)
                                                                    .sum();
                                                            return total > 0 ? total : 1800;
                                                        }
                                                ));

                                        byte[] excelBytes = excelReportGenerator.generateMonthlyReport(map, expectedMap);
                                        String filename = "reporte_asistencia_" + months.get(0) + "_a_" + months.get(months.size() - 1) + ".xlsx";
                                        return Mono.just(ResponseEntity.ok()
                                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                                                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                                .body(excelBytes));
                                    });
                        })
        ).onErrorResume(error -> {
            log.warn("Error al exportar Excel: {}", error.getMessage());
            return Mono.just(ResponseEntity.internalServerError().build());
        });
    }

    @PostMapping("/calculate/{personaId}")
    public Mono<ResponseEntity<ApiResponseDTO<List<AttendanceControllerResponse>>>> calculate(
            @PathVariable UUID personaId,
            @RequestParam(required = false) String date) {
        LocalDate targetDate;
        if (date != null && !date.trim().isEmpty()) {
            try {
                targetDate = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                return Mono.just(ResponseEntity.badRequest().body(
                        ApiResponseDTO.<List<AttendanceControllerResponse>>builder()
                                .message("Formato de fecha inválido. Use yyyy-MM-dd")
                                .data(null)
                                .build()));
            }
        } else {
            targetDate = LocalDate.now();
        }

        return TenantContext.withTenant(tenantId ->
                processAttendanceUseCase.processAttendance(tenantId, personaId, targetDate)
                        .flatMap(this::enrichWithUser)
                        .collectList()
                        .map(list -> ResponseEntity.ok(
                                ApiResponseDTO.ok("Asistencia calculada correctamente", list)))
        );
    }

    @PutMapping("/{id}/justify")
    public Mono<ResponseEntity<ApiResponseDTO<AttendanceControllerResponse>>> justify(
            @PathVariable UUID id,
            @Valid @RequestBody JustifyRequest request,
            @RequestHeader(value = "X-User-Email", defaultValue = "sistema@via.edu.pe") String usuario) {
        return TenantContext.withTenant(tenantId ->
                getAttendanceUseCase.findById(tenantId, id)
                        .flatMap(record -> {
                            record.setJustificado(true);
                            record.setMotivoJustificacion(request.getMotivo());
                            record.setUsuarioJustificacion(usuario);
                            record.setEstado("JUSTIFICADO");
                            return getAttendanceUseCase.save(record)
                                    .flatMap(saved -> enrichWithUser(saved)
                                            .map(dto -> ResponseEntity.ok(
                                                    ApiResponseDTO.ok("Asistencia justificada correctamente", dto))));
                        })
        );
    }

    @GetMapping("/{id}/justification-status")
    public Mono<ResponseEntity<ApiResponseDTO<Map<String, Object>>>> getJustificationStatus(
            @PathVariable UUID id) {
        return TenantContext.withTenant(tenantId ->
                getAttendanceUseCase.findById(tenantId, id)
                        .flatMap(record -> justificationPort.getJustificationStatus(id)
                                .map(status -> {
                                    Map<String, Object> response = new HashMap<>();
                                    response.put("attendanceRecordId", id);
                                    response.put("justificationStatus", status);
                                    response.put("hasJustification", !"NOT_FOUND".equals(status) && !"ERROR".equals(status));

                                    return ResponseEntity.ok(
                                            ApiResponseDTO.ok("Estado de justificación obtenido", response));
                                })
                                .defaultIfEmpty(ResponseEntity.ok(
                                        ApiResponseDTO.ok("No se encontró justificación",
                                                Map.of("attendanceRecordId", id,
                                                        "justificationStatus", "NOT_FOUND",
                                                        "hasJustification", false)))))
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Registro de asistencia no encontrado: " + id))));
    }

    @GetMapping("/recovery-alerts")
    public Mono<ResponseEntity<ApiResponseDTO<List<TeacherRecoveryAlertDTO>>>> getRecoveryAlerts(
            @RequestParam String fechaDesde,
            @RequestParam String fechaHasta) {
        LocalDate fromDate = parseDate(fechaDesde);
        LocalDate toDate = parseDate(fechaHasta);

        if (fromDate == null || toDate == null) {
            return Mono.just(ResponseEntity.badRequest().body(
                    ApiResponseDTO.<List<TeacherRecoveryAlertDTO>>builder()
                            .message("Formato de fecha inválido. Use yyyy-MM-dd")
                            .data(null)
                            .build()));
        }

        return TenantContext.withTenant(tenantId ->
                getAttendanceUseCase.getRecoveryAlerts(tenantId, fromDate, toDate)
                        .flatMap(alert -> usersClient.getUserByPersonId(alert.getPersonaId())
                                .map(user -> {
                                    String name = user.getFullName();
                                    if (name == null || name.isBlank()) {
                                        name = ((user.getFirstName() != null ? user.getFirstName() : "")
                                                + " " + (user.getLastName() != null ? user.getLastName() : "")).trim();
                                    }
                                    alert.setPersonaName(!name.isBlank() ? name
                                            : "Profesor " + alert.getPersonaId().toString().substring(0, 8));
                                    return alert;
                                })
                                .defaultIfEmpty(alert))
                        .map(alert -> TeacherRecoveryAlertDTO.builder()
                                .personaId(alert.getPersonaId())
                                .personaName(alert.getPersonaName())
                                .totalRegistros(alert.getTotalRegistros())
                                .totalTardanzas(alert.getTotalTardanzas())
                                .totalFaltas(alert.getTotalFaltas())
                                .minutosTardanzaAcumulados(alert.getMinutosTardanzaAcumulados())
                                .horasARecuperar(alert.getHorasARecuperar())
                                .requiereRecuperacion(alert.isRequiereRecuperacion())
                                .build())
                        .collectList()
                        .map(list -> ResponseEntity.ok(
                                ApiResponseDTO.ok("Alertas de recuperación obtenidas", list)))
        );
    }

    private Mono<AttendanceControllerResponse> enrichWithUser(RegistroAsistencia record) {
        return usersClient.getUserByPersonId(record.getPersonaId())
                .switchIfEmpty(usersClient.getUserById(record.getPersonaId()))
                .map(user -> AttendanceControllerMapper.toControllerResponse(record, user))
                .defaultIfEmpty(AttendanceControllerMapper.toControllerResponse(record));
    }

    private LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
