package io.github.mridang.codegen.rules;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.github.mridang.codegen.rules.SpecAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DropInternalOperationsRuleTest extends BaseRuleTest<DropInternalOperationsRule> {

    private static Operation internal() {
        final Operation op = new Operation();
        op.addExtension(DropInternalOperationsRule.X_INTERNAL, true);
        return op;
    }

    private static OpenAPI spec() {
        final OpenAPI openAPI = new OpenAPI().path("/keep", new PathItem().get(new Operation()));
        for (int i = 0; i < 200; i++) {
            openAPI.path("/internal/" + i, new PathItem().get(internal()));
        }
        return openAPI;
    }

    @Test
    @DisplayName("Should drop internal operations and the paths they leave empty")
    void shouldDropInternalOperationsAndEmptyPaths() {
        final OpenAPI openAPI = spec();

        rule.apply(openAPI, Map.of(), logger);

        assertThat(openAPI).hasPaths("/keep");
    }

    @Test
    @DisplayName("Should survive being applied to one shared spec from several threads")
    void shouldBeSafeWhenTheSameSpecIsNormalisedConcurrently() throws Exception {
        final OpenAPI shared = spec();
        final ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            final List<Future<Void>> runs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                final Callable<Void> task = () -> {
                    rule.apply(shared, Map.of(), logger);
                    return null;
                };
                runs.add(pool.submit(task));
            }
            for (final Future<Void> run : runs) {
                assertDoesNotThrow(() -> run.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(shared).hasPaths("/keep");
    }
}
