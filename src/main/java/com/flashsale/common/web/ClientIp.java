package com.flashsale.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client IP as resolved by the servlet container. Trusted proxy headers
 * (X-Forwarded-For from internal proxies) are applied via
 * {@code server.forward-headers-strategy=native}.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
