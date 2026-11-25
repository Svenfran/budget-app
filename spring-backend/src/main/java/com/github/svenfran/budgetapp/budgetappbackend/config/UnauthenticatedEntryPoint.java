package com.github.svenfran.budgetapp.budgetappbackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.svenfran.budgetapp.budgetappbackend.constants.ApiErrorCode;
import com.github.svenfran.budgetapp.budgetappbackend.controller.RestResponseEntityExceptionHandler;
import com.github.svenfran.budgetapp.budgetappbackend.dto.ApiErrorDto;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Geschützter Request ohne gültige Anmeldung (fehlender, abgelaufener oder ungültiger Access Token, beendete Session):
 * 401 {@code UNAUTHENTICATED} statt des Spring-Standards 403 (DIV-65). Nur so kann die App „Access Token erneuern“
 * von fachlichem 403 unterscheiden. Body wie im {@link RestResponseEntityExceptionHandler}: mit
 * {@code X-Error-Format: json} {@link ApiErrorDto}, sonst ein kurzer Text.
 */
@Component
public class UnauthenticatedEntryPoint implements AuthenticationEntryPoint {

    static final String MESSAGE = "Unauthenticated";

    private final ObjectMapper objectMapper;

    public UnauthenticatedEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if ("json".equalsIgnoreCase(request.getHeader(RestResponseEntityExceptionHandler.ERROR_FORMAT_HEADER))) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), new ApiErrorDto(ApiErrorCode.UNAUTHENTICATED, MESSAGE, Map.of()));
        } else {
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.getWriter().write(MESSAGE);
        }
    }
}
