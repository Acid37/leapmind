package com.treepeople.leapmindtts.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.ErrorData;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.util.M6RequestIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class M6StrictJsonFilter extends OncePerRequestFilter {
    private static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;
    public static final String STRICT_JSON_PAYLOAD_ATTRIBUTE = M6StrictJsonFilter.class.getName() + ".payload";
    private final M6EventJsonCodec codec;
    private final ObjectMapper json;
    public M6StrictJsonFilter(M6EventJsonCodec codec, ObjectMapper json) { this.codec=codec;this.json=json; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri=request.getRequestURI();
        return !"POST".equals(request.getMethod()) || !(uri.matches("/api/user-profile/[^/]+/record-event") || uri.matches("/api/user-profile/[^/]+/batch-events"));
    }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if (!isJson(request)) {
            chain.doFilter(request, response);
            return;
        }
        byte[] body=request.getInputStream().readNBytes(MAX_REQUEST_BYTES+1);
        if(body.length>MAX_REQUEST_BYTES){bad(request,response);return;}
        try {
            com.fasterxml.jackson.databind.JsonNode payload = codec.parse(body);
            if (payload == null || payload.isNull()) throw new IOException("empty JSON");
            request.setAttribute(STRICT_JSON_PAYLOAD_ATTRIBUTE, payload);
        } catch (Exception invalid) { bad(request,response);return; }
        chain.doFilter(new BodyRequest(request,body),response);
    }

    private boolean isJson(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) return false;
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return "application".equalsIgnoreCase(mediaType.getType())
                    && ("json".equalsIgnoreCase(mediaType.getSubtype())
                    || mediaType.getSubtype().toLowerCase(java.util.Locale.ROOT).endsWith("+json"));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private void bad(HttpServletRequest request,HttpServletResponse response)throws IOException{
        String id=M6RequestIds.resolveOrCreate(request);response.setStatus(400);response.setHeader("X-Request-Id",id);response.setContentType("application/json");
        json.writeValue(response.getOutputStream(),ApiResponse.<ErrorData>builder().code(400).message("请求格式无效").data(new ErrorData(id,"PROFILE_EVENT_INVALID",List.of())).timestamp(System.currentTimeMillis()).build());
    }

    private static final class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] body; BodyRequest(HttpServletRequest request,byte[] body){super(request);this.body=body;}
        @Override public ServletInputStream getInputStream(){ByteArrayInputStream input=new ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){}};}
        @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        @Override public int getContentLength(){return body.length;}
        @Override public long getContentLengthLong(){return body.length;}
    }
}
