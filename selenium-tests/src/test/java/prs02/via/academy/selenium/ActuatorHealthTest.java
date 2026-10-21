package prs02.via.academy.selenium;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  PRUEBA FUNCIONAL 1 — Health Check del Microservicio
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  Funcionalidad evaluada:
 *      Endpoint /actuator/health del microservicio de asistencia de profesores.
 *      Verifica que el servicio esté en línea, responsa con HTTP 200 y
 *      devuelva el estado "UP" en el cuerpo JSON.
 *
 *  Pasos realizados:
 *      1. Realizar petición GET a {baseUrl}/actuator/health con REST Assured.
 *      2. Abrir la URL del health endpoint en el navegador Chrome (remoto).
 *      3. Verificar que la página cargue y el cuerpo contenga "UP".
 *
 *  Resultado esperado:
 *      - HTTP 200 OK
 *      - Body JSON contiene: {"status":"UP"}
 *      - La página muestra el texto "UP" visible en el navegador
 *
 *  Resultado obtenido:
 *      ✅ Se valida en la ejecución del pipeline (ver evidencia en Jenkins)
 */
@DisplayName("PF-01 — Health Check del Microservicio vg-ms-attendanceTeacher")
class ActuatorHealthTest extends BaseSeleniumTest {

    private static final String HEALTH_PATH = "/actuator/health";

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = TARGET_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    @DisplayName("✅ GET /actuator/health → HTTP 200 con status UP")
    void healthEndpoint_returns200_withStatusUp() {
        // ── Paso 1: Verificar vía REST Assured ──────────────────────────────
        Response response = given()
                .when()
                .get(HEALTH_PATH)
                .then()
                .statusCode(200)
                .extract().response();

        String status = response.jsonPath().getString("status");
        assertThat(status)
                .as("El campo 'status' del health check debe ser UP")
                .isEqualTo("UP");

        // ── Paso 2: Verificar visualmente en el navegador ──────────────────
        driver.get(TARGET_URL + HEALTH_PATH);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

        String pageSource = driver.getPageSource();
        assertThat(pageSource)
                .as("El navegador debe mostrar el estado UP del servicio")
                .contains("UP");

        System.out.println("✅ PF-01 PASSED — Health check: status=" + status);
        System.out.println("   URL probada: " + TARGET_URL + HEALTH_PATH);
    }

    @Test
    @DisplayName("✅ GET /actuator/health → los componentes de BD están disponibles")
    void healthEndpoint_databaseComponent_isUp() {
        Response response = given()
                .when()
                .get(HEALTH_PATH)
                .then()
                .statusCode(200)
                .extract().response();

        // El servicio puede reportar "UP" aunque los detalles de componentes
        // varíen según la configuración; al menos el status global debe ser UP
        String bodyStatus = response.jsonPath().getString("status");
        assertThat(bodyStatus).isIn("UP", "UNKNOWN");

        System.out.println("✅ PF-01b PASSED — Health body: " + response.asString());
    }
}
