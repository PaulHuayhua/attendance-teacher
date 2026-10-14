package prs02.via.academy.application.input;

import prs02.via.academy.domain.model.ImportResult;
import reactor.core.publisher.Mono;

/**
 * Caso de uso: importar un archivo de marcaciones biométricas exportado
 * desde un dispositivo ZKTeco / Anviz vía USB.
 *
 * El archivo puede ser .dat, .txt o .csv con líneas:
 *   PIN<TAB>DateTime<TAB>Status<TAB>Verify
 *   00012<TAB>2026-09-17 08:03:00<TAB>0<TAB>1
 *
 * También acepta formato CSV con cabecera:
 *   pin,dateTime,status,verify
 */
public interface ImportBiometricFileUseCase {

    /**
     * @param content  bytes del archivo subido
     * @param filename nombre original del archivo (para detectar formato)
     * @return resumen del procesamiento
     */
    Mono<ImportResult> importFile(byte[] content, String filename);
}
