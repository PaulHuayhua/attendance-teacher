package prs02.via.academy.infrastructure.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import prs02.via.academy.infrastructure.adapters.input.dto.BiometricFileRecord;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsea archivos exportados por relojes biométricos ZKTeco / Anviz.
 *
 * Formatos soportados:
 *
 * 1. TAB-delimited sin cabecera (formato nativo ZKTeco .dat):
 *    PIN<TAB>DateTime<TAB>Status<TAB>Verify
 *    00012<TAB>2026-09-17 08:03:00<TAB>0<TAB>1
 *
 * 2. CSV con cabecera (cualquier delimitador , o ; o TAB):
 *    pin,dateTime,status,verify
 *    00012,2026-09-17 08:03:00,0,1
 *
 * 3. Formato ATT (common export):
 *    No.	UserID	DateTime
 *    1	00012	2026-09-17 08:03:00
 */
@Slf4j
@Component
public class BiometricFileParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    );

    /**
     * @param content  bytes del archivo
     * @param filename nombre original (usado para detectar formato)
     * @return lista de registros parseados (sin filtrar duplicados)
     */
    public ParseResult parse(byte[] content, String filename) {
        List<BiometricFileRecord> records = new ArrayList<>();
        List<String> parseErrors = new ArrayList<>();
        int totalLines = 0;
        int skipped = 0;

        // Intentar UTF-8 primero, luego Latin-1 (relojes antiguos)
        String text = tryDecode(content);
        String[] lines = text.split("\\r?\\n");
        String delimiter = detectDelimiter(lines);
        boolean headerSkipped = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            totalLines++;

            // Saltar línea de cabecera detectada
            if (!headerSkipped && isHeaderLine(line, delimiter)) {
                headerSkipped = true;
                skipped++;
                log.debug("Cabecera detectada y omitida: {}", line);
                continue;
            }

            String[] parts = line.split(delimiter);

            // Normalizar: eliminar comillas si existen
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim().replaceAll("^\"|\"$", "");
            }

            BiometricFileRecord record = tryParseRecord(parts, line, parseErrors);
            if (record != null) {
                records.add(record);
            } else {
                skipped++;
            }
        }

        return new ParseResult(records, totalLines, skipped, parseErrors);
    }

    // ──────────────────────────────────────────────────────────────────────────

    private String tryDecode(byte[] content) {
        for (Charset charset : List.of(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1,
                Charset.forName("windows-1252"))) {
            try {
                return new String(content, charset);
            } catch (Exception ignored) {
            }
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private String detectDelimiter(String[] lines) {
        // Tomar las primeras 5 líneas no vacías para detectar
        for (String line : lines) {
            if (line.isBlank()) continue;
            if (line.contains("\t")) return "\t";
            if (line.contains(";")) return ";";
            if (line.contains(",")) return ",";
        }
        return "\t"; // Default ZKTeco
    }

    private boolean isHeaderLine(String line, String delimiter) {
        String lower = line.toLowerCase();
        // Cabeceras comunes en exportaciones biométricas
        return lower.startsWith("no.") || lower.startsWith("no\t")
                || lower.contains("userid") || lower.contains("user id")
                || lower.contains("pin") && lower.contains("datetime")
                || lower.contains("pin") && lower.contains("date")
                || lower.contains("employeeid") || lower.contains("emp_id");
    }

    /**
     * Intenta parsear una línea según varios formatos de columnas conocidos.
     * Retorna null si la línea no es válida.
     */
    private BiometricFileRecord tryParseRecord(String[] parts, String rawLine, List<String> errors) {
        if (parts.length < 2) {
            if (errors.size() < 20) errors.add("Línea inválida (pocas columnas): " + rawLine);
            return null;
        }

        // Formato 1: PIN | DateTime | [Status] | [Verify]
        // Formato 2: No. | PIN | DateTime
        // Formato 3: DateTime | PIN
        String pin = null;
        LocalDateTime dateTime = null;

        // Intento 1: col[0]=PIN, col[1]=DateTime
        if (looksLikePin(parts[0]) && looksLikeDateTime(parts[1])) {
            pin = normalizePin(parts[0]);
            dateTime = parseDateTime(parts[1]);
        }
        // Intento 2: col[0]=No., col[1]=PIN, col[2]=DateTime
        else if (parts.length >= 3 && looksLikePin(parts[1]) && looksLikeDateTime(parts[2])) {
            pin = normalizePin(parts[1]);
            dateTime = parseDateTime(parts[2]);
        }
        // Intento 3: col[0]=DateTime, col[1]=PIN
        else if (looksLikeDateTime(parts[0]) && looksLikePin(parts[1])) {
            dateTime = parseDateTime(parts[0]);
            pin = normalizePin(parts[1]);
        }

        if (pin == null || dateTime == null) {
            if (errors.size() < 20) {
                errors.add("No se pudo parsear (formato desconocido): " + rawLine);
            }
            return null;
        }

        if (dateTime.toLocalDate().isAfter(java.time.LocalDate.now())) {
            if (errors.size() < 20) {
                errors.add("Fecha futura ignorada: " + rawLine);
            }
            return null;
        }

        return new BiometricFileRecord(pin, dateTime);
    }

    private boolean looksLikePin(String s) {
        if (s == null || s.isBlank()) return false;
        // PIN puede ser numérico o alfanumérico, máx. 20 chars
        return s.length() <= 20 && s.matches("[A-Za-z0-9]+");
    }

    private boolean looksLikeDateTime(String s) {
        if (s == null || s.length() < 10) return false;
        // Debe contener un separador de fecha
        return s.matches(".*\\d{4}.*\\d{2}.*\\d{2}.*") || s.matches(".*\\d{2}/\\d{2}/\\d{4}.*");
    }

    private String normalizePin(String pin) {
        // Quitar ceros a la izquierda para comparar con el código del empleado
        return pin.replaceFirst("^0+(?!$)", "").trim();
    }

    private LocalDateTime parseDateTime(String raw) {
        String cleaned = raw.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(cleaned, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        log.warn("No se pudo parsear la fecha: {}", raw);
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────────

    public record ParseResult(
            List<BiometricFileRecord> records,
            int totalLines,
            int skipped,
            List<String> parseErrors
    ) {}
}
