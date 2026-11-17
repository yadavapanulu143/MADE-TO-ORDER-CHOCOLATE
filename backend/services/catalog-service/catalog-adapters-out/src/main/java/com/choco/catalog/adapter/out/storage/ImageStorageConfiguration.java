package com.choco.catalog.adapter.out.storage;

import com.choco.catalog.application.port.ImageStoragePort;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the image-storage adapter.
 *
 * <p>The storage root is externalised through {@code choco.catalog.image-storage.root} rather than
 * hard-coded, so a deployment points it at a mounted volume without a code change. It defaults to a
 * path under the working directory, which is suitable for local development and for the tests.
 */
@Configuration(proxyBeanMethods = false)
class ImageStorageConfiguration {

    @Bean
    ImageStoragePort imageStoragePort(
            @Value("${choco.catalog.image-storage.root:./data/catalog-images}") String root) {
        return new LocalImageStorageAdapter(Path.of(root));
    }
}
