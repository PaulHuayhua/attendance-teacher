package prs02.via.academy.domain.model;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Registro del último contacto de un dispositivo ZKTeco con el servidor ADMS.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceHeartbeat {

    /** Número de serie del dispositivo (campo SN en el protocolo ADMS). */
    private String serialNumber;

    /** Dirección IP desde la que contactó. */
    private String ipAddress;

    /** Cuándo fue el último contacto (registro o heartbeat). */
    private LocalDateTime lastSeen;

    /** Cuántas marcaciones ha enviado en total desde que se registró. */
    private int totalMarksSent;
}
