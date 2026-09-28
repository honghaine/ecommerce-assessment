package com.flashsale.common.error;

import java.net.URI;

import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

import com.flashsale.common.logging.CorrelationIdFilter;

public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), code.message());
        problem.setType(URI.create("urn:flashsale:error:" + code.name().toLowerCase()));
        problem.setTitle(code.status().getReasonPhrase());
        problem.setProperty("code", code.name());
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }
}
