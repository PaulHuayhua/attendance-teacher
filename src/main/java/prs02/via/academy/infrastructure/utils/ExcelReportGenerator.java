package prs02.via.academy.infrastructure.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import prs02.via.academy.infrastructure.adapters.input.dto.AttendanceControllerResponse;

import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.*;

@Slf4j
@Component
public class ExcelReportGenerator {

    public byte[] generateMonthlyReport(Map<YearMonth, List<AttendanceControllerResponse>> dataByMonth,
                                          Map<UUID, Integer> expectedWeeklyMinutesByTeacher) {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle alertStyle = createAlertStyle(workbook);

            for (Map.Entry<YearMonth, List<AttendanceControllerResponse>> entry : dataByMonth.entrySet()) {
                YearMonth month = entry.getKey();
                List<AttendanceControllerResponse> records = entry.getValue();

                String sheetName = month.getMonth().toString().substring(0, 3) + " " + month.getYear();
                Sheet sheet = workbook.createSheet(sheetName);

                String[] columns = {
                    "Profesor", "Fecha", "Día", "Turno", "Hora Esperada",
                    "Hora Real", "Tardanza (min)", "Min. Trabajados",
                    "Estado", "Justificado", "Motivo Justificación"
                };

                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < columns.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(columns[i]);
                    cell.setCellStyle(headerStyle);
                }

                for (int i = 0; i < records.size(); i++) {
                    Row row = sheet.createRow(i + 1);
                    AttendanceControllerResponse r = records.get(i);

                    row.createCell(0).setCellValue(nullSafe(r.getPersonaName()));
                    row.createCell(1).setCellValue(r.getFecha() != null ? r.getFecha().toString() : "");
                    row.createCell(2).setCellValue(nullSafe(r.getDiaSemana()));
                    row.createCell(3).setCellValue(nullSafe(r.getTurno()));
                    row.createCell(4).setCellValue(formatTimeSafe(r.getHoraEntradaEsperada()));
                    row.createCell(5).setCellValue(formatTimeSafe(r.getHoraEntradaReal()));
                    row.createCell(6).setCellValue(r.getMinutosTardanza() != null ? r.getMinutosTardanza() : 0);
                    row.createCell(7).setCellValue(r.getMinutosTrabajados() != null ? r.getMinutosTrabajados() : 0);
                    row.createCell(8).setCellValue(nullSafe(r.getEstado()));
                    row.createCell(9).setCellValue(Boolean.TRUE.equals(r.getJustificado()) ? "Sí" : "No");
                    row.createCell(10).setCellValue(nullSafe(r.getMotivoJustificacion()));

                    if (r.getMinutosTardanza() != null && r.getMinutosTardanza() > 0) {
                        for (int j = 0; j < columns.length; j++) {
                            row.getCell(j).setCellStyle(alertStyle);
                        }
                    }

                    if (i % 2 == 0) {
                        // subtle alternating rows would go here if needed
                    }
                }

                int weeklyStartRow = records.size() + 2;
                Row weeklyHeader = sheet.createRow(weeklyStartRow);
                Cell weeklyTitleCell = weeklyHeader.createCell(0);
                weeklyTitleCell.setCellValue("CONTROL SEMANAL DE HORAS");
                weeklyTitleCell.setCellStyle(headerStyle);
                sheet.addMergedRegion(new CellRangeAddress(weeklyStartRow, weeklyStartRow, 0, 5));

                Row weeklyColHeader = sheet.createRow(weeklyStartRow + 1);
                String[] weeklyCols = {"Profesor", "Semana", "Semana del", "Total Min. Trabajados", "Horas", "Alerta"};
                for (int i = 0; i < weeklyCols.length; i++) {
                    Cell cell = weeklyColHeader.createCell(i);
                    cell.setCellValue(weeklyCols[i]);
                    cell.setCellStyle(headerStyle);
                }

                WeekFields weekFields = WeekFields.of(Locale.forLanguageTag("es-PE"));
                Map<String, Map<Integer, List<AttendanceControllerResponse>>> byTeacherWeek = new LinkedHashMap<>();
                for (var rec : records) {
                    if (rec.getFecha() == null) continue;
                    String name = rec.getPersonaName() != null ? rec.getPersonaName() : "Sin nombre";
                    int weekNum = rec.getFecha().get(weekFields.weekOfWeekBasedYear());
                    byTeacherWeek.computeIfAbsent(name, k -> new LinkedHashMap<>())
                            .computeIfAbsent(weekNum, k -> new ArrayList<>())
                            .add(rec);
                }

                int weeklyRowIdx = weeklyStartRow + 2;
                for (var teacherEntry : byTeacherWeek.entrySet()) {
                    int expectedWeeklyMin = 0;
                    AttendanceControllerResponse sample = teacherEntry.getValue().values().stream()
                            .flatMap(Collection::stream)
                            .findFirst().orElse(null);
                    if (sample != null && sample.getPersonaId() != null) {
                        expectedWeeklyMin = expectedWeeklyMinutesByTeacher.getOrDefault(sample.getPersonaId(), 1800);
                    }

                    for (var weekEntry : teacherEntry.getValue().entrySet()) {
                        Row row = sheet.createRow(weeklyRowIdx++);
                        int totalMin = weekEntry.getValue().stream()
                                .mapToInt(r -> r.getMinutosTrabajados() != null ? r.getMinutosTrabajados() : 0)
                                .sum();
                        LocalDate firstDay = weekEntry.getValue().stream()
                                .map(AttendanceControllerResponse::getFecha)
                                .filter(Objects::nonNull)
                                .min(Comparator.naturalOrder())
                                .orElse(null);
                        LocalDate weekStart = firstDay != null
                                ? firstDay.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                : null;

                        row.createCell(0).setCellValue(teacherEntry.getKey());
                        row.createCell(1).setCellValue("Sem " + weekEntry.getKey());
                        row.createCell(2).setCellValue(weekStart != null ? weekStart.toString() : "-");
                        row.createCell(3).setCellValue(totalMin);
                        row.createCell(4).setCellValue(String.format("%.1f", totalMin / 60.0));

                        int diff = expectedWeeklyMin - totalMin;
                        boolean underExpected = diff > 0;
                        Cell alertCell = row.createCell(5);
                        if (underExpected) {
                            alertCell.setCellValue("⚠ FALTAN " + diff + " min (" + String.format("%.1f", diff / 60.0) + "h) — COMPLETAR CON HORAS EXTRAS");
                            alertCell.setCellStyle(alertStyle);
                            for (int j = 0; j < 6; j++) {
                                row.getCell(j).setCellStyle(alertStyle);
                            }
                        } else {
                            alertCell.setCellValue("OK (+" + Math.abs(diff) + " min)");
                        }
                    }
                }

                sheet.setAutoFilter(new CellRangeAddress(0, records.size(), 0, columns.length - 1));
                for (int i = 0; i < columns.length; i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generando reporte Excel: {}", e.getMessage());
            throw new RuntimeException("Error al generar archivo Excel", e);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("dd/MM/yyyy"));
        return style;
    }

    private CellStyle createAlertStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);
        return style;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private String formatTimeSafe(java.time.LocalTime time) {
        return time != null ? time.toString().substring(0, 5) : "";
    }
}
