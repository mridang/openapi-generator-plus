package io.github.mridang.codegen;

import io.swagger.v3.oas.models.OpenAPI;
import java.util.Map;
import org.openapitools.codegen.OpenAPINormalizer;

/**
 * The stock normaliser with {@link SpecPreChecks} run first, so the checks see the document
 * before the stock OpenAPI 3.1 rewrite drops keywords from array schemas. Every generator
 * selects it unless the caller names another normaliser class.
 */
public class PreNormalizer extends OpenAPINormalizer {

    /**
     * Constructs the normaliser.
     *
     * @param openAPI    The document to normalise.
     * @param inputRules The normaliser rules.
     */
    public PreNormalizer(OpenAPI openAPI, Map<String, String> inputRules) {
        super(openAPI, inputRules);
    }

    @Override
    protected void normalize() {
        SpecPreChecks.run(this.openAPI, LOGGER);
        super.normalize();
    }
}
