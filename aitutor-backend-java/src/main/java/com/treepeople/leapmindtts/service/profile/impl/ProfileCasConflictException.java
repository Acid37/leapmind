package com.treepeople.leapmindtts.service.profile.impl;

/** Raised when the profile CAS update hits a moved base version; the whole batch rolls back and the next scan retries. */
public class ProfileCasConflictException extends RuntimeException {
    public ProfileCasConflictException(Long userId, long baseVersion) {
        super("profile CAS conflict for user " + userId + " at version " + baseVersion);
    }
}
