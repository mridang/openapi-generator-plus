package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.github.mridang.codegen.rules.SpecAssertions.HttpMethod;
import static io.github.mridang.codegen.rules.SpecAssertions.assertThat;

public class StripParametersRuleTest extends BaseRuleTest<StripParametersRule> {

    @Test
    @DisplayName("Should remove specified parameters from an operation")
    public void shouldRemoveSpecifiedParameters() {
        OpenAPI openAPI = new OpenAPI()
            .path("/test", new PathItem()
                .get(new Operation()
                    .addParametersItem(new Parameter().name("Connect-Protocol-Version"))
                    .addParametersItem(new Parameter().name("paramToKeep"))
                    .addParametersItem(new Parameter().name("Connect-Timeout-Ms"))
                )
            );

        Map<String, String> config = Map.of(StripParametersRule.RULE_VALUE_KEY, "Connect-Protocol-Version|Connect-Timeout-Ms");
        rule.apply(openAPI, config, logger);

        assertThat(openAPI).forOperation(HttpMethod.GET, "/test").hasParameters("paramToKeep");
    }

    @Test
    @DisplayName("Should remove parameters using case-insensitive matching")
    public void shouldBeCaseInsensitive() {
        OpenAPI openAPI = new OpenAPI()
            .path("/test", new PathItem()
                .get(new Operation()
                    .addParametersItem(new Parameter().name("Connect-Protocol-Version"))
                    .addParametersItem(new Parameter().name("paramToKeep"))
                    .addParametersItem(new Parameter().name("Connect-Timeout-Ms"))
                )
            );

        Map<String, String> config = Map.of(StripParametersRule.RULE_VALUE_KEY, "connect-protocol-version");
        rule.apply(openAPI, config, logger);

        assertThat(openAPI).forOperation(HttpMethod.GET, "/test").hasParameters("paramToKeep", "Connect-Timeout-Ms");
    }

    @Test
    @DisplayName("Should remove a referenced parameter from an operation")
    public void shouldRemoveReferencedParameter() {
        OpenAPI openAPI = new OpenAPI()
            .components(new Components()
                .addParameters("Shared-Header-To-Remove", new Parameter().name("Shared-Header-To-Remove").in("header"))
            )
            .path("/ref-test", new PathItem()
                .get(new Operation()
                    .addParametersItem(new Parameter().$ref("#/components/parameters/Shared-Header-To-Remove"))
                    .addParametersItem(new Parameter().name("paramToKeep"))
                )
            );

        Map<String, String> config = Map.of(StripParametersRule.RULE_VALUE_KEY, "Shared-Header-To-Remove");
        rule.apply(openAPI, config, logger);

        assertThat(openAPI)
            .hasParameterComponents("Shared-Header-To-Remove")
            .forOperation(HttpMethod.GET, "/ref-test")
            .hasParameters("paramToKeep");
    }
}
