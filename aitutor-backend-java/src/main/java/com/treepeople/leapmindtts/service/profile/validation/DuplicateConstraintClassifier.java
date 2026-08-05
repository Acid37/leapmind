package com.treepeople.leapmindtts.service.profile.validation;

import java.sql.SQLException;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;

public final class DuplicateConstraintClassifier {
    private static final Pattern EXACT_KEY = Pattern.compile(
            "(?i)for key ['`](?:[^'`]*\\.)?uk_user_events_event_id['`]");
    private DuplicateConstraintClassifier() { }

    public static boolean isEventId(DuplicateKeyException error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062
                    && EXACT_KEY.matcher(String.valueOf(sql.getMessage())).find()) return true;
            cause = cause.getCause();
        }
        return false;
    }
}
