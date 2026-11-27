package io.github.mridang.codegen.generators;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Yaml;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the values of the document as written that the parsed model cannot carry.
 *
 * <p>swagger-parser 2.1 drops the OpenAPI 3.1 License Object {@code identifier}, so the generator
 * reads it from the raw tree of the specification file. A license held in {@code components} and
 * reached through {@code $ref} is not read.
 */
public final class RawSpecReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(RawSpecReader.class);

    private RawSpecReader() {}

    /**
     * Returns the SPDX identifier that {@code info.license.identifier} declares, or null when the
     * document declares none or the location cannot be read.
     *
     * @param location the path of the specification file, or null
     * @return the identifier, trimmed, or null
     */
    @Nullable public static String licenseIdentifier(@Nullable final String location) {
        final JsonNode root = read(location);
        final JsonNode info = root == null ? null : root.get("info");
        final JsonNode license = info == null ? null : info.get("license");
        final JsonNode identifier = license == null ? null : license.get("identifier");
        if (identifier == null || !identifier.isTextual() || identifier.asText().isBlank()) {
            return null;
        }
        return identifier.asText().trim();
    }

    @Nullable private static JsonNode read(@Nullable final String location) {
        if (location == null) {
            return null;
        }
        try {
            return Yaml.mapper().readTree(new File(location));
        } catch (IOException e) {
            LOGGER.debug("Cannot read {} as the raw specification: {}", location, e.getMessage());
            return null;
        }
    }
}
