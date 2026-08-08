package com.treepeople.leapmindtts.service.profile.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.ErrorData;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.util.M6RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class M6SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper mapper;
    public M6SecurityErrorHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
        write(request, response, 401, "PROFILE_UNAUTHENTICATED", "未认证");
    }
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException e) throws IOException {
        write(request, response, 403, "PROFILE_ACCESS_DENIED", "无权访问");
    }
    private void write(HttpServletRequest request, HttpServletResponse response, int status,
                       String errorCode, String message) throws IOException {
        String requestId = M6RequestIds.resolveOrCreate(request);
        response.setStatus(status);
        response.setHeader("X-Request-Id", requestId);
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), ApiResponse.<ErrorData>builder().code(status).message(message)
                .data(new ErrorData(requestId, errorCode, List.of())).timestamp(System.currentTimeMillis()).build());
    }
}
