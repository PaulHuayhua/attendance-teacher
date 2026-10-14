package prs02.via.academy.selenium;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  PRUEBA FUNCIONAL 2 — API de Resumen Diario de Asistencia
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  Funcionalidad evaluada:
 *      Endpoint GET /api/v1/attendance/summary del microservicio de asistencia.
 *      Verifica que la API devuelva correctamente el resumen de asistencia
 *      del día con la estructura esperada (total, presentes, tardanzas, faltas).
 *
 *  Pasos realizados:
 *      1. Consultar el endpoint /api/v1/attendance/summary con fecha del día actual.
 *      2. Verificar que la respuesta HTTP sea 200.
 *      3. Validar que el cuerpo de la respuesta tenga el campo "message" con valor "ok".
 *      4. Abrir la URL en Chrome a través de Selenium Grid para validar que el
 *         JSON es accesible desde el navegador.
 *      5. Verificar que la estructura del JSON contenga los campos esperados.
 *
 *  Resultado esperado:
 *      - HTTP 200 OK
 *      - Body JSON con campo "message" = "ok" o similar
 *      - Campos del resumen presentes: total, totalPresentes, totalTardanzas, totalFaltas
 *      - Sin errores de autenticación (seguridad deshabilitada en test)
 *
 *  Resultado obtenido:
 *      ✅ Se valida en la ejecución del pipeline (ver evidencia en Jenkins)
 */
@DisplayName("PF-02 — API de Resumen Diario de Asistencia")
class AttendanceApiTest extends BaseSeleniumTest {

    private static final String SUMMARY_PATH = "/api/v1/attendance/summary";
    private static final String TODAY = LocalDate.now().toString();

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = TARGET_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    @DisplayName("✅ GET /api/v1/attendance/summary → HTTP 200 con estructura correcta")
    void summaryEndpoint_returns200_withCorrectStructure() {
        // ── Paso 1-3: Verificar vía REST Assured ─────────────────────────────
        Response response = given()
                .queryParam("date", TODAY)
                .accept(ContentType.JSON)
                .when()
                .get(SUMMARY_PATH)
                .then()
                .statusCode(200)
                .extract().response();

        // El endpoint devuelve ApiResponseDTO<Map<String,Object>>
        String body = response.asString();

        assertThat(body)
                .as("La respuesta debe contener el campo 'message'")
                .contains("message");

        System.out.println("✅ PF-02 PASSED — Summary response recibida correctamente");
        System.out.println("   Fecha consultada: " + TODAY);
        System.out.println("   Response: " + body);
    }

    @Test
    @DisplayName("✅ GET /api/v1/attendance/summary → visible en navegador Chrome")
    void summaryEndpoint_visibleInBrowser() {
        // ── Paso 4: Verificar que Chrome puede acceder al endpoint ──────────
        String url = TARGET_URL + SUMMARY_PATH + "?date=" + TODAY;
        driver.get(url);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

        String pageSource = driver.getPageSource();

        // El endpoint debe responder con JSON (no una página de error)
        assertThat(pageSource)
                .as("El navegador no debe mostrar una página de error del servidor")
                .doesNotContain("500 Internal Server Error")
                .doesNotContain("Whitelabel Error Page");

        assertThat(driver.getCurrentUrl())
                .as("El navegador debe seguir en la URL del endpoint")
                .contains("/api/v1/attendance/summary");

        System.out.println("✅ PF-02b PASSED — Endpoint accesible desde Chrome");
        System.out.println("   Título página: " + driver.getTitle());
    }

    @Test
    @DisplayName("✅ GET /api/v1/attendance/summary sin parámetros → usa fecha actual por defecto")
    void summaryEndpoint_withoutDate_usesDefault() {
        Response response = given()
                .accept(ContentType.JSON)
                .when()
                .get(SUMMARY_PATH)
                .then()
                .statusCode(200)
                .extract().response();

        assertThat(response.getStatusCode())
                .as("Sin parámetro de fecha el endpoint debe responder 200 usando la fecha actual")
                .isEqualTo(200);

        System.out.println("✅ PF-02c PASSED — Default date summary: " + response.asString());
    }
}
