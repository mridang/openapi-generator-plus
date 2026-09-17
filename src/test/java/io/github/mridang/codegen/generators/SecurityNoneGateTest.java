package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards {@link AbstractBetterCodegen#isSecurityNone}, the gate that decides
 * whether an operation suppresses the client-level authenticator via the no-auth
 * sentinel.
 *
 * <p>Regression coverage for the "Zitadel shape" auth-drop: a spec with one
 * non-empty global {@code security} requirement and operations that declare no
 * security of their own. Those operations INHERIT the global requirement and
 * must keep the client-level authenticator; they must not be mistaken for
 * {@code security: []}. The original fix gated on {@code hasAuthMethods}, which
 * is false for both inherited-global and explicit-empty operations, so it
 * suppressed auth on every inherited-auth call.
 */
class SecurityNoneGateTest {

    private static OpenAPI withGlobalSecurity(List<SecurityRequirement> global) {
        final OpenAPI api = new OpenAPI();
        api.setSecurity(global);
        return api;
    }

    private static SecurityRequirement requirement(String scheme) {
        return new SecurityRequirement().addList(scheme);
    }

    /** The Zitadel shape: global non-empty requirement, operation inherits it. */
    @Test
    void inheritedGlobalAuthIsNotSecurityNone() {
        final OpenAPI api = withGlobalSecurity(List.of(requirement("zitadelAccessToken")));
        final Operation op = new Operation();
        op.setOperationId("GetIDPByID");
        // security ABSENT -> inherits the global requirement -> must NOT suppress.
        assertFalse(
                AbstractBetterCodegen.isSecurityNone(op, api),
                "an operation inheriting a non-empty global requirement must keep auth");
    }

    /** Operation-level security: [] -> genuinely unauthenticated. */
    @Test
    void explicitEmptyOperationSecurityIsSecurityNone() {
        final OpenAPI api = withGlobalSecurity(List.of(requirement("zitadelAccessToken")));
        final Operation op = new Operation();
        op.setOperationId("testEcho");
        op.setSecurity(new ArrayList<>()); // security: []
        assertTrue(
                AbstractBetterCodegen.isSecurityNone(op, api),
                "an operation with explicit empty security is unauthenticated");
    }

    /** Operation-level security present and non-empty -> secured, not suppressed. */
    @Test
    void explicitNonEmptyOperationSecurityIsNotSecurityNone() {
        final OpenAPI api = withGlobalSecurity(List.of(requirement("zitadelAccessToken")));
        final Operation op = new Operation();
        op.setOperationId("addPet");
        op.setSecurity(List.of(requirement("petstore_auth")));
        assertFalse(
                AbstractBetterCodegen.isSecurityNone(op, api),
                "an operation with its own non-empty requirement is secured");
    }

    /** Global security: [] inherited by an operation -> unauthenticated by inheritance. */
    @Test
    void inheritedEmptyGlobalSecurityIsSecurityNone() {
        final OpenAPI api = withGlobalSecurity(new ArrayList<>()); // global security: []
        final Operation op = new Operation();
        op.setOperationId("ping");
        assertTrue(
                AbstractBetterCodegen.isSecurityNone(op, api),
                "an operation inheriting an explicit empty global requirement has no auth");
    }

    /** No global security and no operation security -> absent, not empty -> not suppressed. */
    @Test
    void absentEverywhereIsNotSecurityNone() {
        final OpenAPI api = new OpenAPI(); // no global security
        final Operation op = new Operation();
        op.setOperationId("whoami");
        assertFalse(
                AbstractBetterCodegen.isSecurityNone(op, api),
                "absent security is not the same as an explicit empty requirement");
    }
}
