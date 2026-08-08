package com.treepeople.leapmindtts.service.virtualteacher;

import java.util.Optional;

public interface AudioStorageService {
    void store(String objectKey, byte[] data, String contentType);
    Optional<byte[]> load(String objectKey);
    String createReadUrl(String objectKey);
}
