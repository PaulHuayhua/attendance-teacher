package prs02.via.academy.application.input;

import prs02.via.academy.domain.model.DeviceHeartbeat;
import reactor.core.publisher.Mono;

/**
 * Caso de uso para el protocolo PUSH/ADMS de ZKTeco.
 *
 * El dispositivo (WL30) llama a estos métodos automáticamente
 * una vez configurado con la URL del servidor.
 */
public interface AdmsUseCase {

    /**
     * Registra o actualiza el heartbeat de un dispositivo.
     * Llamado en GET /iclock/cdata (registro inicial) y GET /iclock/getrequest (heartbeat).
     *
     * @param serialNumber  número de serie del dispositivo (campo SN)
     * @param ipAddress     IP remota de la petición
     * @return heartbeat actualizado
     */
    Mono<DeviceHeartbeat> registerHeartbeat(String serialNumber, String ipAddress);

    /**
     * Procesa un bloque de logs de asistencia enviados por el dispositivo.
     * Llamado en POST /iclock/cdata?table=ATTLOG
     *
     * Formato de cada línea (TAB-delimitado):
     *   PIN  DateTime           Status  Verify  WorkCode  Reserved
     *   001  2026-09-17 08:03:00  0       1       0         0
     *
     * @param serialNumber número de serie del dispositivo
     * @param body         cuerpo del POST con las líneas de marcación
     * @return número de marcaciones procesadas
     */
    Mono<Integer> processAttLog(String serialNumber, String body);

    /**
     * Devuelve el estado actual de todos los dispositivos registrados.
     */
    Mono<DeviceHeartbeat> getDeviceStatus(String serialNumber);
}
