package prs02.via.academy.domain.model;

import lombok.*;

import java.util.List;

/**
 * Resultado del proceso de importación de un archivo biométrico.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResult {

    /** Número de líneas leídas del archivo. */
    private int totalLines;

    /** Número de marcaciones guardadas correctamente. */
    private int processed;

    /** Número de líneas omitidas (duplicadas, formato inválido, etc.). */
    private int skipped;

    /** Número de líneas con error. */
    private int errors;

    /** Detalle de los errores encontrados (máx. 20). */
    private List<String> errorDetails;
}
