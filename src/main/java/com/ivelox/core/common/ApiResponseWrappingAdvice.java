package com.ivelox.core.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps every successful REST response body in ApiResponse, except raw byte payloads
 * (e.g. served images) and empty (204 No Content / Void) bodies, which pass through untouched.
 */
@RestControllerAdvice(basePackages = "com.ivelox.core")
public class ApiResponseWrappingAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> type = returnType.getParameterType();
        return type != byte[].class && type != Void.class && type != void.class;
    }

    @Override
    public Object beforeBodyWrite(
            Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response
    ) {
        if (body instanceof ApiResponse<?>) {
            return body;
        }
        return ApiResponse.ok(body);
    }
}
