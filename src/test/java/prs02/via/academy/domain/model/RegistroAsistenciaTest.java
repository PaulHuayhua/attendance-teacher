package prs02.via.academy.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas unitarias del modelo RegistroAsistencia.
 * Verifican construcción, builder y estado por defecto.
 */
@DisplayName("RegistroAsistencia — Modelo de Dominio")
class RegistroAsistenciaTest {

    @Test
    @DisplayName("✅ Builder crea registro con todos los campos correctos")
    void builder_createsFullRecord() {
        UUID id        = UUID.randomUUID();
        UUID personaId = UUID.randomUUID();
        LocalDate fecha = LocalDate.of(2024, 10, 15);

        RegistroAsistencia record = RegistroAsistencia.builder()
                .id(id)
                .personaId(personaId)
                .fecha(fecha)
                .diaSemana("MARTES")
                .turno("MAÑANA")
                .horaEntradaEsperada(LocalTime.of(8, 0))
                .horaEntradaReal(LocalTime.of(8, 10))
                .minutosTardanza(10)
                .minutosTrabajados(300)
                .estado("TARDE")
                .justificado(false)
                .build();

        assertThat(record.getId()).isEqualTo(id);
        assertThat(record.getPersonaId()).isEqualTo(personaId);
        assertThat(record.getFecha()).isEqualTo(fecha);
        assertThat(record.getDiaSemana()).isEqualTo("MARTES");
        assertThat(record.getTurno()).isEqualTo("MAÑANA");
        assertThat(record.getMinutosTardanza()).isEqualTo(10);
        assertThat(record.getEstado()).isEqualTo("TARDE");
        assertThat(record.getJustificado()).isFalse();
    }

    @Test
    @DisplayName("✅ NoArgsConstructor + setters funcionan correctamente")
    void noArgsConstructor_withSetters_works() {
        RegistroAsistencia record = new RegistroAsistencia();
        record.setEstado("PRESENTE");
        record.setFecha(LocalDate.now());
        record.setPersonaId(UUID.randomUUID());

        assertThat(record.getEstado()).isEqualTo("PRESENTE");
        assertThat(record.getFecha()).isNotNull();
        assertThat(record.getPersonaId()).isNotNull();
    }

    @Test
    @DisplayName("✅ Dos registros con el mismo ID son independientes en memoria")
    void twoRecords_withSameId_areIndependent() {
        UUID sharedId = UUID.randomUUID();

        RegistroAsistencia r1 = RegistroAsistencia.builder()
                .id(sharedId).estado("PRESENTE").build();
        RegistroAsistencia r2 = RegistroAsistencia.builder()
                .id(sharedId).estado("FALTA").build();

        assertThat(r1.getEstado()).isNotEqualTo(r2.getEstado());
        assertThat(r1.getId()).isEqualTo(r2.getId());
    }

    @Test
    @DisplayName("✅ Estado JUSTIFICADO se puede asignar correctamente")
    void estado_justificado_canBeSet() {
        RegistroAsistencia record = RegistroAsistencia.builder()
                .estado("JUSTIFICADO")
                .justificado(true)
                .motivoJustificacion("Permiso institucional")
                .usuarioJustificacion("admin@via.edu.pe")
                .build();

        assertThat(record.getEstado()).isEqualTo("JUSTIFICADO");
        assertThat(record.getJustificado()).isTrue();
        assertThat(record.getMotivoJustificacion()).isEqualTo("Permiso institucional");
    }
}
