package com.treepeople.leapmindtts.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;

public final class M6RequestIds {
    private static final String ATTRIBUTE = M6RequestIds.class.getName() + ".requestId";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private M6RequestIds() { }
    public static String resolveOrCreate(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof String id) return id;
        String header = request.getHeader("X-Request-Id");
        String id = header != null && SAFE.matcher(header).matches() ? header : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        return id;
    }
}
