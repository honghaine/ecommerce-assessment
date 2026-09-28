package com.flashsale.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import com.flashsale.common.error.ErrorCode;
import com.flashsale.common.error.ProblemDetails;

/**
 * Writes RFC 7807 bodies for authentication (401) and authorization (403) failures
 * raised inside the Spring Security filter chain.
 */
@Component
public class ProblemSecurityHandlers {

    private final JsonMapper jsonMapper;

    public ProblemSecurityHandlers(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> write(response, ErrorCode.UNAUTHORIZED);
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ProblemDetails.of(code));
    }
}
