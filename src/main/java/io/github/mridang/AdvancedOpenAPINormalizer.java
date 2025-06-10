package io.github.mridang;

import io.swagger.v3.oas.models.OpenAPI;
import org.openapitools.codegen.OpenAPINormalizer;

import java.util.Map;

public class AdvancedOpenAPINormalizer extends OpenAPINormalizer {
    public AdvancedOpenAPINormalizer(OpenAPI openAPI, Map<String, String> inputRules) {
        super(openAPI, inputRules);
    }
}
