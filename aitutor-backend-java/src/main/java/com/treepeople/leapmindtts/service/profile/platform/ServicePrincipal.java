package com.treepeople.leapmindtts.service.profile.platform;

import java.util.regex.Pattern;

/** Opaque internal service identity; it is not sourced from an HTTP request field. */
public record ServicePrincipal(String value) {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    public ServicePrincipal { if (value == null || !ID.matcher(value).matches()) throw new IllegalArgumentException("service principal is invalid"); }
}
