package io.github.mridang.codegen.generators;

import com.fasterxml.jackson.databind.JsonNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.core.util.Yaml;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the values of the document as written that the parsed model cannot carry.
 *
 * <p>swagger-models 2.2 has no field for the OpenAPI 3.2 Response Object {@code summary}, and
 * swagger-parser 2.1 drops the OpenAPI 3.1 License Object {@code identifier}. The generator reads
 * the raw tree of the specification file for those values, once per generation, and hands them to
 * the templates through the shared generator. A reference that is not followed (a response or a
 * license held in {@code components} and reached through {@code $ref}) is not read.
 */
public final class RawSpecReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(RawSpecReader.class);

    /** The path-item fields that hold an operation object. */
    private static final String[] OPERATION_FIELDS = {
        "get", "put", "post", "delete", "options", "head", "patch", "trace"
    };

    private RawSpecReader() {}

    /**
     * Returns the response summaries of every operation, keyed by {@code "METHOD path"} with the
     * path exactly as it is written in the document. Each value maps the response status code
     * (or {@code default}) to its summary, in document order. Responses without a non-blank
     * summary are left out, and an operation with none is left out.
     *
     * @param location the path of the specification file, or null
     * @return the summaries, empty when the location is null, unreadable or has no paths
     */
    public static Map<String, Map<String, String>> responseSummaries(@Nullable final String location) {
        final Map<String, Map<String, String>> summaries = new LinkedHashMap<>();
        final JsonNode root = read(location);
        final JsonNode paths = root == null ? null : root.get("paths");
        if (paths == null || !paths.isObject()) {
            return summaries;
        }
        final Iterator<Map.Entry<String, JsonNode>> pathItems = paths.properties().iterator();
        while (pathItems.hasNext()) {
            final Map.Entry<String, JsonNode> pathItem = pathItems.next();
            if (!pathItem.getValue().isObject()) {
                continue;
            }
            for (final String field : OPERATION_FIELDS) {
                final JsonNode operation = pathItem.getValue().get(field);
                if (operation == null || !operation.isObject()) {
                    continue;
                }
                final Map<String, String> byCode = summariesOf(operation.get("responses"));
                if (!byCode.isEmpty()) {
                    summaries.put(
                            field.toUpperCase(Locale.ROOT) + " " + pathItem.getKey(), byCode);
                }
            }
        }
        return summaries;
    }

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

    /**
     * Returns the Discriminator Object {@code defaultMapping} of every component schema that
     * declares one, keyed by the component name, as the schema name the value references (the
     * last segment of a {@code $ref} value, or the value itself when it is a bare schema name).
     * swagger-models 2.2 has no field for it (OpenAPI 3.2, #87).
     *
     * @param location the path of the specification file, or null
     * @return the default schema name per component, in document order
     */
    public static Map<String, String> discriminatorDefaults(@Nullable final String location) {
        final Map<String, String> defaults = new LinkedHashMap<>();
        final JsonNode root = read(location);
        final JsonNode components = root == null ? null : root.get("components");
        final JsonNode schemas = components == null ? null : components.get("schemas");
        if (schemas == null || !schemas.isObject()) {
            return defaults;
        }
        for (final Map.Entry<String, JsonNode> entry : schemas.properties()) {
            final JsonNode discriminator = entry.getValue().get("discriminator");
            final JsonNode value = discriminator == null ? null : discriminator.get("defaultMapping");
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                final String text = value.asText().trim();
                defaults.put(entry.getKey(), text.substring(text.lastIndexOf('/') + 1));
            }
        }
        return defaults;
    }

    /**
     * The OpenAPI 3.2 fields of a Security Scheme Object that swagger-models 2.2 has no field for:
     * {@code deprecated}, {@code oauth2MetadataUrl} and the {@code deviceAuthorization} flow
     * (issues 91 and 92).
     */
    public static final class RawSecurityScheme {
        private final boolean deprecated;
        @Nullable private final String oauth2MetadataUrl;
        @Nullable private final String deviceAuthorizationUrl;
        @Nullable private final String deviceTokenUrl;
        @Nullable private final String deviceRefreshUrl;
        private final Map<String, String> deviceScopes;

        RawSecurityScheme(boolean deprecated, @Nullable String oauth2MetadataUrl,
                @Nullable String deviceAuthorizationUrl, @Nullable String deviceTokenUrl,
                @Nullable String deviceRefreshUrl, Map<String, String> deviceScopes) {
            this.deprecated = deprecated;
            this.oauth2MetadataUrl = oauth2MetadataUrl;
            this.deviceAuthorizationUrl = deviceAuthorizationUrl;
            this.deviceTokenUrl = deviceTokenUrl;
            this.deviceRefreshUrl = deviceRefreshUrl;
            this.deviceScopes = deviceScopes;
        }

        /** Whether the scheme is marked {@code deprecated: true}. */
        public boolean deprecated() {
            return deprecated;
        }

        /** The scheme's {@code oauth2MetadataUrl} (RFC 8414), or null. */
        @Nullable public String oauth2MetadataUrl() {
            return oauth2MetadataUrl;
        }

        /** Whether the scheme declares a device authorization flow with both of its URLs. */
        public boolean hasDeviceFlow() {
            return deviceAuthorizationUrl != null && deviceTokenUrl != null;
        }

        /** The device flow's {@code deviceAuthorizationUrl}, or null. */
        @Nullable public String deviceAuthorizationUrl() {
            return deviceAuthorizationUrl;
        }

        /** The device flow's {@code tokenUrl}, or null. */
        @Nullable public String deviceTokenUrl() {
            return deviceTokenUrl;
        }

        /** The device flow's {@code refreshUrl}, or null. */
        @Nullable public String deviceRefreshUrl() {
            return deviceRefreshUrl;
        }

        /** The device flow's scopes in declaration order. */
        public Map<String, String> deviceScopes() {
            return deviceScopes;
        }
    }

    /**
     * Returns the OpenAPI 3.2 fields of every security scheme declared inline under {@code
     * components.securitySchemes}, in document order. A scheme held behind a {@code $ref} is not
     * read.
     *
     * @param location the path of the specification file, or null
     * @return the fields per scheme name, empty when the location is null or unreadable
     */
    public static Map<String, RawSecurityScheme> securitySchemes(@Nullable final String location) {
        final Map<String, RawSecurityScheme> out = new LinkedHashMap<>();
        final JsonNode root = read(location);
        final JsonNode components = root == null ? null : root.get("components");
        final JsonNode schemes = components == null ? null : components.get("securitySchemes");
        if (schemes == null || !schemes.isObject()) {
            return out;
        }
        for (final Map.Entry<String, JsonNode> entry : schemes.properties()) {
            final JsonNode scheme = entry.getValue();
            final JsonNode deprecated = scheme.get("deprecated");
            final JsonNode flows = scheme.get("flows");
            final JsonNode device = flows == null ? null : flows.get("deviceAuthorization");
            final Map<String, String> scopes = new LinkedHashMap<>();
            final JsonNode scopeNode = device == null ? null : device.get("scopes");
            if (scopeNode != null && scopeNode.isObject()) {
                for (final Map.Entry<String, JsonNode> scope : scopeNode.properties()) {
                    scopes.put(scope.getKey(), scope.getValue().asText(""));
                }
            }
            out.put(entry.getKey(), new RawSecurityScheme(
                    deprecated != null && deprecated.asBoolean(false),
                    text(scheme.get("oauth2MetadataUrl")),
                    text(device == null ? null : device.get("deviceAuthorizationUrl")),
                    text(device == null ? null : device.get("tokenUrl")),
                    text(device == null ? null : device.get("refreshUrl")),
                    java.util.Collections.unmodifiableMap(scopes)));
        }
        return out;
    }

    @Nullable private static String text(@Nullable final JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText().trim() : null;
    }

    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = "The location is the input specification the generator was asked to read")
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

    private static Map<String, String> summariesOf(@Nullable final JsonNode responses) {
        final Map<String, String> byCode = new LinkedHashMap<>();
        if (responses == null || !responses.isObject()) {
            return byCode;
        }
        final Iterator<Map.Entry<String, JsonNode>> codes = responses.properties().iterator();
        while (codes.hasNext()) {
            final Map.Entry<String, JsonNode> response = codes.next();
            if (!response.getValue().isObject()) {
                continue;
            }
            final JsonNode summary = response.getValue().get("summary");
            if (summary != null && summary.isTextual() && !summary.asText().isBlank()) {
                byCode.put(response.getKey(), summary.asText().trim());
            }
        }
        return byCode;
    }
}
