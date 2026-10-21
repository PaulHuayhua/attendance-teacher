package prs02.via.academy.infrastructure.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable);

        if (securityEnabled) {
            http.authorizeExchange(auth -> auth
                            .pathMatchers("/actuator/**").permitAll()
                            .pathMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                            // Los endpoints ADMS son consumidos por el dispositivo físico ZKTeco,
                            // el cual no puede enviar JWT. Se exponen públicos deliberadamente.
                            .pathMatchers("/api/v1/iclock/**").permitAll()
                            .anyExchange().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())))
                    .addFilterAfter(new AuthHeaderFilter(), SecurityWebFiltersOrder.AUTHORIZATION);
        } else {
            http.authorizeExchange(auth -> auth.anyExchange().permitAll());
        }

        return http.build();
    }

    private org.springframework.web.cors.reactive.CorsConfigurationSource corsConfigurationSource() {
        // ── Configuración para endpoints protegidos (requieren credenciales) ──
        org.springframework.web.cors.CorsConfiguration secured = new org.springframework.web.cors.CorsConfiguration();
        secured.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "http://localhost:3000",
                "https://lab.vallegrande.edu.pe"
        ));
        secured.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        secured.setAllowedHeaders(List.of("*"));
        secured.setAllowCredentials(true);
        secured.setMaxAge(3600L);

        // ── Configuración para endpoints ADMS (dispositivo físico + simulador) ──
        // Usa allowedOriginPatterns para soportar file:// (origen "null") y
        // cualquier localhost sin restricción de credenciales.
        org.springframework.web.cors.CorsConfiguration adms = new org.springframework.web.cors.CorsConfiguration();
        adms.setAllowedOriginPatterns(List.of("*"));
        adms.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        adms.setAllowedHeaders(List.of("*"));
        adms.setAllowCredentials(false);
        adms.setMaxAge(3600L);

        org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource source =
                new org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/iclock/**", adms);
        source.registerCorsConfiguration("/**", secured);
        return source;
    }
}
