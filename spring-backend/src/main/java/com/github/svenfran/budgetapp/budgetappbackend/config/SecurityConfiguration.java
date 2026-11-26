package com.github.svenfran.budgetapp.budgetappbackend.config;

import com.github.svenfran.budgetapp.budgetappbackend.controller.RestResponseEntityExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final List<String> allowedOrigins;

    private final JwtAuthenticationFilter jwtAuthFilter;

    private final AuthenticationProvider authenticationProvider;

    private final UnauthenticatedEntryPoint unauthenticatedEntryPoint;

    public SecurityConfiguration(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins,
                                 JwtAuthenticationFilter jwtAuthFilter, AuthenticationProvider authenticationProvider,
                                 UnauthenticatedEntryPoint unauthenticatedEntryPoint) {
        this.allowedOrigins = allowedOrigins;
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
        this.unauthenticatedEntryPoint = unauthenticatedEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf()
                .disable()
                .cors()
                .configurationSource(corsConfigurationSource())
                .and()
                .authorizeHttpRequests()
                .antMatchers("/api/auth/**", "/api/userprofile/password-reset", "/ws/**", "/wss/**")
                .permitAll()
                .antMatchers(HttpMethod.OPTIONS, "/**")
                .permitAll()
                .anyRequest()
                .authenticated()
                .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // nicht angemeldet -> 401 UNAUTHENTICATED statt 403 (DIV-65)
                .exceptionHandling()
                .authenticationEntryPoint(unauthenticatedEntryPoint)
                .and()
                // Logout ist ein normaler Endpunkt (AuthenticationController), nicht Springs LogoutFilter
                .logout()
                .disable()
        ;

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // X-Error-Format: Opt-in der App ins JSON-Fehlerformat (DIV-108); fehlt er hier, scheitert der Preflight neuer Apps
        configuration.setAllowedHeaders(Arrays.asList("Origin", "Content-Type", "Accept", "Authorization", "DeviceId",
                RestResponseEntityExceptionHandler.ERROR_FORMAT_HEADER));
        // Login-Rate-Limit (DIV-64): Restzeit der 429-Antwort für die App lesbar machen
        configuration.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
