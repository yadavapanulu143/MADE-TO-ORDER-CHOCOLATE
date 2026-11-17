package com.choco.catalog.application.fake;

import com.choco.catalog.application.port.ImageStoragePort;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory {@link ImageStoragePort} that keeps stored bytes in a map.
 *
 * <p>Mirrors the real adapter's contract that the key is generated, not supplied: {@link #store}
 * returns a fresh random key and never reads one from the caller. A test can therefore assert that an
 * upload attaches a key that the caller could not have chosen.
 */
public class InMemoryImageStorage implements ImageStoragePort {

    private final Map<String, StoredImage> stored = new LinkedHashMap<>();

    @Override
    public String store(UUID productId, String contentType, byte[] content) {
        String key = productId + "/" + UUID.randomUUID();
        stored.put(key, new StoredImage(content.clone(), contentType));
        return key;
    }

    @Override
    public StoredImage read(String storageKey) {
        StoredImage image = stored.get(storageKey);
        if (image == null) {
            throw new com.choco.core.error.NotFoundException(
                    com.choco.core.error.ErrorCode.NOT_FOUND, "Image not found");
        }
        return image;
    }

    @Override
    public void delete(String storageKey) {
        stored.remove(storageKey);
    }

    public int size() {
        return stored.size();
    }

    public boolean contains(String storageKey) {
        return stored.containsKey(storageKey);
    }
}
