package prs02.via.academy.infrastructure.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import prs02.via.academy.application.input.ProcessAttendanceUseCase;

@Component
@ConditionalOnProperty(name = "attendance.processing.enabled", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class AttendanceProcessingJob {

    private final ProcessAttendanceUseCase processAttendanceUseCase;

    @Scheduled(cron = "0 0 * * * *")
    public void processPendingMarks() {
        log.info("Iniciando job automático de procesamiento de marcaciones pendientes...");

        processAttendanceUseCase.processAllPending()
                .count()
                .doOnSuccess(count -> log.info("Job completado. Se procesaron {} registros de asistencia.", count))
                .doOnError(error -> log.error("Error al ejecutar job de procesamiento: ", error))
                .subscribe();
    }
}
