package io.github.mridang.codegen.generators.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mridang.codegen.generators.ProbeGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenServer;

/**
 * Validates {@link BetterNodeCodegen#quoteSingle(String)}, which turns an
 * apiKey scheme name into a TypeScript string literal. A legal header name
 * may contain a single quote or a backslash, which must not break the
 * generated source.
 */
class BetterNodeCodegenTest {

    @Test
    void quotesAPlainName() {
        assertEquals("'X-Api-Key'", BetterNodeCodegen.quoteSingle("X-Api-Key"));
    }

    @Test
    void escapesQuoteAndBackslash() {
        assertEquals("'X-Api\\'Key\\\\'", BetterNodeCodegen.quoteSingle("X-Api'Key\\"));
    }

    @Test
    void leavesDollarLiteral() {
        assertEquals("'$apiKey'", BetterNodeCodegen.quoteSingle("$apiKey"));
    }

    @Test
    void uploadParametersAcceptABlobAsWellAsABuffer() {
        final CodegenParameter file = new CodegenParameter();
        file.isFile = true;
        file.dataType = "Array<Buffer>";
        assertEquals("Array<Buffer | Blob>", BetterNodeCodegen.uploadType(file));
        file.dataType = "Buffer";
        assertEquals("Buffer | Blob", BetterNodeCodegen.uploadType(file));
    }

    @Test
    void nonUploadParametersKeepTheirType() {
        final CodegenParameter plain = new CodegenParameter();
        plain.dataType = "string";
        assertEquals("string", BetterNodeCodegen.uploadType(plain));
    }

    @Test
    void modelsNamedLikeTheGlobalErrorOrResultGetThePrefix() {
        final BetterNodeCodegen codegen = new BetterNodeCodegen();
        assertEquals("ModelError", codegen.toModelName("Error"));
        assertEquals("ModelResult", codegen.toModelName("Result"));
        assertEquals("Category", codegen.toModelName("Category"));
    }

    @Test
    void parametersNamedLikeInjectedArgumentsGetASuffix() {
        final BetterNodeCodegen codegen = new BetterNodeCodegen();
        assertEquals("optionsParam", codegen.toParamName("options"));
        assertEquals("requestOptionsParam", codegen.toParamName("requestOptions"));
        assertEquals("serverParam", codegen.toParamName("server"));
        assertEquals("petId", codegen.toParamName("petId"));
    }

    private static CodegenOperation operation(
            final boolean withServer, final boolean withQuery, final boolean optionsRequired) {
        final CodegenOperation op = new CodegenOperation();
        op.operationId = "getThing";
        final CodegenParameter pathParam = new CodegenParameter();
        pathParam.paramName = "thingId";
        pathParam.dataType = "number";
        op.pathParams.add(pathParam);
        if (withQuery) {
            final CodegenParameter query = new CodegenParameter();
            query.paramName = "filter";
            query.dataType = "string";
            op.queryParams.add(query);
        }
        if (withServer) {
            op.servers = new ArrayList<>(List.of(new CodegenServer()));
        }
        final Map<String, Object> decorator = new HashMap<>();
        decorator.put("optionsParamRequired", optionsRequired);
        op.vendorExtensions.put("op", decorator);
        return op;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> wrapperFields(final CodegenOperation op) {
        final Map<String, Object> decorator =
                java.util.Objects.requireNonNull((Map<String, Object>) op.vendorExtensions.get("op"));
        return java.util.Objects.requireNonNull(
                (List<Map<String, Object>>) decorator.get("requestWrapperFields"));
    }

    private static List<String> names(final List<Map<String, Object>> fields) {
        final List<String> names = new ArrayList<>();
        for (final Map<String, Object> field : fields) {
            names.add((String) field.get("name"));
        }
        return names;
    }

    @Test
    void theServerComesAfterTheRequiredParametersAndBeforeTheOptionsObject() {
        final CodegenOperation op = operation(true, true, false);
        new BetterNodeCodegen().populateRequestWrapper(op);
        assertEquals(
                List.of("thingId", "server", "options", "requestOptions"), names(wrapperFields(op)));
    }

    @Test
    void aServerOnlySignatureEndsWithTheRequestOptions() {
        final CodegenOperation op = operation(true, false, false);
        new BetterNodeCodegen().populateRequestWrapper(op);
        assertEquals(List.of("thingId", "server", "requestOptions"), names(wrapperFields(op)));
    }

    @Test
    void securityAddsNoArgumentToTheSignature() {
        final CodegenOperation op = operation(false, false, false);
        op.hasAuthMethods = true;
        new BetterNodeCodegen().populateRequestWrapper(op);
        assertEquals(List.of("thingId", "requestOptions"), names(wrapperFields(op)));
    }

    @Test
    void anOptionsObjectNeverCarriesAPerCallAuthenticator() {
        final CodegenOperation op = operation(false, true, false);
        op.hasAuthMethods = true;
        final Map<String, Object> context = new HashMap<>();
        final BetterNodeCodegen codegen = new BetterNodeCodegen();
        codegen.injectAuthFieldContext(op, context);
        assertEquals(false, context.get("hasAuthField"));
    }

    @Test
    void anOperationWithNoParametersOfItsOwnHasNoOptionsFile() {
        final CodegenOperation op = operation(false, false, false);
        op.hasAuthMethods = true;
        assertEquals(null, new BetterNodeCodegen().generateOptionsFileContent(op, List.of(), "GetThingOptions"));
    }

    @Test
    void aServerBeforeARequiredOptionsObjectIsSpelledOutNotOptional() {
        final CodegenOperation op = operation(true, true, true);
        new BetterNodeCodegen().populateRequestWrapper(op);
        final Map<String, Object> server = wrapperFields(op).get(1);
        assertEquals("GetThingServer | undefined", server.get("type"));
        assertEquals(false, server.get("optional"));
    }

    @Test
    void aServerBeforeAnOptionalOptionsObjectIsOptional() {
        final CodegenOperation op = operation(true, true, false);
        new BetterNodeCodegen().populateRequestWrapper(op);
        final Map<String, Object> server = wrapperFields(op).get(1);
        assertEquals("GetThingServer", server.get("type"));
        assertEquals(true, server.get("optional"));
    }

    @Test
    void anOperationWithoutAServerHasNoServerArgument() {
        final CodegenOperation op = operation(false, true, false);
        new BetterNodeCodegen().populateRequestWrapper(op);
        assertEquals(List.of("thingId", "options", "requestOptions"), names(wrapperFields(op)));
    }

    @Test
    void anIntegerEnumMemberTakesTheMemberCasingOfTheStringMembers() {
        final BetterNodeCodegen codegen = new BetterNodeCodegen();
        assertEquals("Number1", codegen.toEnumVarName("1", "number"));
        assertEquals("NumberMinus1", codegen.toEnumVarName("-1", "number"));
        assertEquals("Red", codegen.toEnumVarName("red", "String"));
        assertEquals("OnHold", codegen.toEnumVarName("on-hold", "String"));
    }

    @Test
    void aDateParameterAlsoAcceptsADate() {
        final CodegenParameter date = new CodegenParameter();
        date.isDate = true;
        date.dataType = "string";
        assertEquals("string | Date", BetterNodeCodegen.uploadType(date));
        assertEquals("string | Date", BetterNodeCodegen.dateInputType(date, "string"));
    }

    @Test
    void anArrayOfDatesAcceptsDates() {
        final CodegenParameter dates = new CodegenParameter();
        dates.isArray = true;
        dates.items = new org.openapitools.codegen.CodegenProperty();
        dates.items.isDate = true;
        dates.dataType = "Array<string>";
        assertEquals("Array<string | Date>", BetterNodeCodegen.uploadType(dates));
        dates.dataType = "Set<string>";
        assertEquals("Set<string | Date>", BetterNodeCodegen.uploadType(dates));
    }

    @Test
    void aPlainStringParameterIsNotWidenedToADate() {
        final CodegenParameter text = new CodegenParameter();
        text.dataType = "string";
        assertEquals("string", BetterNodeCodegen.uploadType(text));
    }

    private static BetterNodeCodegen configured(Map<String, Object> options) {
        final BetterNodeCodegen codegen = new BetterNodeCodegen();
        codegen.additionalProperties().putAll(options);
        codegen.processOpts();
        return codegen;
    }

    @Test
    void thePackageMetadataOptionsReachTheTemplates() {
        final Map<String, Object> props = configured(Map.of(
                "repositoryUrl", "https://example.com/acme/client.git",
                "homepageUrl", "https://example.com/client",
                "authors", "Acme \"Platform\" Team",
                "issueTrackerUrl", "https://example.com/client/issues",
                "documentationUrl", "https://docs.example.com/client",
                "license", "Apache-2.0")).additionalProperties();
        assertEquals("https://example.com/acme/client.git", props.get("repositoryUrl"));
        assertEquals("Acme \\\"Platform\\\" Team", props.get("authorsJson"));
        assertEquals("https://example.com/client", props.get("homepageUrlJson"));
        assertEquals("https://example.com/client/issues", props.get("issueTrackerUrlJson"));
        assertEquals("https://docs.example.com/client", props.get("documentationUrl"));
        assertEquals("Apache-2.0", props.get("licenseId"));
    }

    @Test
    void licenseIdWinsOverLicense() {
        final Map<String, Object> props = configured(Map.of("license", "Apache-2.0", "licenseId", "MIT"))
                .additionalProperties();
        assertEquals("MIT", props.get("licenseId"));
    }

    @Test
    void theLicenseDefaultsToMit() {
        assertEquals("MIT", configured(Map.of()).additionalProperties().get("licenseId"));
    }

    @Test
    void anUnsetOrBlankMetadataOptionLeavesNoSectionBehind() {
        final Map<String, Object> props = configured(Map.of("repositoryUrl", "  ", "homepageUrl", "")).additionalProperties();
        for (final String key : List.of("repositoryUrl", "repositoryUrlJson", "homepageUrl", "homepageUrlJson",
                "authors", "authorsJson", "issueTrackerUrl", "issueTrackerUrlJson", "documentationUrl")) {
            assertFalse(props.containsKey(key), key);
        }
    }

    @Test
    void jsonEscapingKeepsAManifestValueValid() {
        assertEquals("a\\\"b\\\\c\\nd", BetterNodeCodegen.jsonEscape("a\"b\\c\nd"));
        assertEquals("\\u0001", BetterNodeCodegen.jsonEscape("\u0001"));
    }

    private static final String DATE_SPEC = String.join("\n",
            "openapi: 3.0.3",
            "info: {title: Dates, version: 1.0.0}",
            "paths:",
            "  /bookings:",
            "    post:",
            "      operationId: createBooking",
            "      parameters:",
            "        - {name: since, in: query, schema: {type: string, format: date}}",
            "        - name: days",
            "          in: query",
            "          schema: {type: array, items: {type: string, format: date}}",
            "      requestBody:",
            "        content:",
            "          application/json:",
            "            schema: {$ref: '#/components/schemas/Booking'}",
            "      responses:",
            "        '200':",
            "          description: ok",
            "          content:",
            "            application/json:",
            "              schema: {$ref: '#/components/schemas/Booking'}",
            "components:",
            "  schemas:",
            "    Booking:",
            "      type: object",
            "      required: [checkIn]",
            "      properties:",
            "        checkIn: {type: string, format: date}",
            "        holidays: {type: array, items: {type: string, format: date}}",
            "        ratio: {type: number, format: double}",
            "        stamp: {type: string, format: date-time}",
            "");

    private static String generatedFile(Path out, String relative) throws IOException {
        return Files.readString(out.resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path generateDateSpec(String name) throws IOException {
        final Path out = Path.of("target", "node-date-spec", name).toAbsolutePath();
        final Path spec = Files.createTempFile("node-dates", ".yaml");
        Files.writeString(spec, DATE_SPEC, StandardCharsets.UTF_8);
        ProbeGenerator.generate(
                "node-plus", Map.of("skipFormatter", "true", "npmName", "dates-client"), out, spec.toString());
        return out;
    }

    @Test
    void aDateFieldIsAPlainStringTheDecodeCheckValidates() throws IOException {
        final String model = generatedFile(generateDateSpec("model"), "src/models/booking.ts");
        assertTrue(model.contains("readonly checkIn!: string;"), model);
        assertTrue(
                model.contains("Object.assign(this, { checkIn: plainDate(this.checkIn, 'checkIn') });"),
                model);
        assertTrue(model.contains("readonly holidays?: Array<string>;"), model);
        assertTrue(model.contains("plainDate(__e, 'holidays element')"), model);
        assertTrue(model.contains("plainDate"), model);
        assertTrue(model.matches("(?s).*import \\{[^}]*\\bplainDate\\b[^}]*} from '../object-serializer.js';.*"), model);
        assertTrue(model.contains("readonly stamp?: Date;"), model);
    }

    @Test
    void theConstructorValidatesNothingAndTheDecodeCheckDoes() throws IOException {
        final String model = generatedFile(generateDateSpec("construct"), "src/models/booking.ts");
        final int constructor = model.indexOf("constructor(data?: Partial<Booking>) {");
        final int validate = model.indexOf("__validate(): void {");
        final int copy = model.indexOf("with(patch: Partial<Booking>)");
        assertTrue(constructor > 0 && constructor < validate && validate < copy, model);
        final String constructorBody = model.substring(constructor, validate);
        assertFalse(constructorBody.contains("throw "), constructorBody);
        assertFalse(constructorBody.contains("plainDate"), constructorBody);
        final String validateBody = model.substring(validate, copy);
        assertTrue(validateBody.contains("throw new TypeError('checkIn is required');"), validateBody);
        assertTrue(validateBody.contains("ratio must be a finite number"), validateBody);
    }

    @Test
    void aDateTimeFieldKeepsItsDecorators() throws IOException {
        final String model = generatedFile(generateDateSpec("datetime"), "src/models/booking.ts");
        final String stamp = model.substring(model.indexOf("@Expose({ name: 'stamp' })"));
        assertTrue(stamp.startsWith("@Expose({ name: 'stamp' })\n  @Type(() => Date)"), stamp);
        final String checkIn = model.substring(model.indexOf("@Expose({ name: 'checkIn' })"));
        assertTrue(checkIn.startsWith("@Expose({ name: 'checkIn' })\n  readonly checkIn!: string;"), checkIn);
    }

    @Test
    void aDoubleFieldMustBeFinite() throws IOException {
        final String model = generatedFile(generateDateSpec("double"), "src/models/booking.ts");
        assertTrue(model.contains("!Number.isFinite(this.ratio)"), model);
        assertTrue(model.contains("ratio must be a finite number"), model);
    }

    @Test
    void everyModelHasAWithCopyMethod() throws IOException {
        final String model = generatedFile(generateDateSpec("with"), "src/models/booking.ts");
        assertTrue(model.contains("with(patch: Partial<Booking>): Booking {"), model);
        assertTrue(model.contains("new Booking({ ...this, ...patch })"), model);
    }

    @Test
    void aDateParameterAcceptsADateInTheOptionsAndIsFormattedAsADate() throws IOException {
        final Path out = generateDateSpec("options");
        final String options = generatedFile(out, "src/api/options/create-booking-options.ts");
        assertTrue(options.contains("readonly since?: string | Date;"), options);
        assertTrue(options.contains("readonly days?: Array<string | Date>;"), options);
        final String api = generatedFile(out, "src/api/default-api.ts");
        assertTrue(api.contains("ValueSerializer.stringifyDate(options.since)"), api);
        assertTrue(api.contains("ValueSerializer.stringifyDates("), api);
    }

    private static final String SCHEME_SPEC = String.join("\n",
            "openapi: 3.1.0",
            "info: {title: Acme Billing SDK, version: 1.0.0}",
            "paths:",
            "  /invoices:",
            "    get:",
            "      operationId: listInvoices",
            "      security:",
            "        - partnerToken: []",
            "      responses:",
            "        '200': {description: ok}",
            "components:",
            "  securitySchemes:",
            "    partnerToken: {type: http, scheme: bearer}",
            "    serviceToken: {type: http, scheme: bearer}",
            "    ledgerKey: {type: apiKey, in: header, name: X-Ledger-Key}",
            "    adminLogin: {type: http, scheme: basic}",
            "  schemas:",
            "    Colour:",
            "      type: string",
            "      enum: [red, dark-green]",
            "    Paint:",
            "      type: object",
            "      properties:",
            "        colour:",
            "          $ref: '#/components/schemas/Colour'",
            "          default: dark-green",
            "");

    private static Path generateSchemeSpec(String name, Map<String, Object> extra) throws IOException {
        final Path out = Path.of("target", "node-scheme-spec", name).toAbsolutePath();
        final Path spec = Files.createTempFile("node-schemes", ".yaml");
        Files.writeString(spec, SCHEME_SPEC, StandardCharsets.UTF_8);
        final Map<String, Object> options = new HashMap<>(Map.of("skipFormatter", "true"));
        options.putAll(extra);
        ProbeGenerator.generate("node-plus", options, out, spec.toString());
        return out;
    }

    @Test
    void aSchemeAuthenticatorReadsTheVariableTheSharedGeneratorNamedForIt() throws IOException {
        final Path out = generateSchemeSpec("env", Map.of("envVarPrefix", "acme"));
        final String partner = generatedFile(out, "src/auth/partner-token-authenticator.ts");
        assertTrue(partner.contains("resolveCredential(token, 'ACME_PARTNER_TOKEN_TOKEN', 'Bearer token')"), partner);
        final String service = generatedFile(out, "src/auth/service-token-authenticator.ts");
        assertTrue(service.contains("'ACME_SERVICE_TOKEN_TOKEN'"), service);
        final String ledger = generatedFile(out, "src/auth/ledger-key-authenticator.ts");
        assertTrue(ledger.contains("resolveCredential(apiKey, 'ACME_API_KEY', 'API key for \\'X-Ledger-Key\\'')"), ledger);
        final String admin = generatedFile(out, "src/auth/admin-login-authenticator.ts");
        assertTrue(admin.contains("resolveCredential(username, 'ACME_USERNAME', 'Basic auth username')"), admin);
        assertTrue(admin.contains("resolveCredential(password, 'ACME_PASSWORD', 'Basic auth password')"), admin);
    }

    @Test
    void theBaseAuthenticatorsDefaultToTheSharedPrefix() throws IOException {
        final Path out = generateSchemeSpec("prefix", Map.of());
        final String bearer = generatedFile(out, "src/auth/bearer-authenticator.ts");
        assertTrue(bearer.contains("const TOKEN_VARIABLE = 'ACME_BILLING_TOKEN';"), bearer);
        final String credentials = generatedFile(out, "src/auth/credentials.ts");
        assertFalse(credentials.contains("ENV_PREFIX"), credentials);
    }

    @Test
    void aReferencedEnumWithASiblingDefaultStartsAtThatDefault() throws IOException {
        final String paint = generatedFile(generateSchemeSpec("sibling", Map.of()), "src/models/paint.ts");
        assertTrue(paint.contains("readonly colour?: Colour = Colour.DarkGreen;"), paint);
        assertTrue(paint.contains("this.colour = Colour.DarkGreen;"), paint);
    }

    @Test
    void theClientTakesOneOptionalCredentialPerSchemeNamedAfterTheScheme() throws IOException {
        final String client = generatedFile(generateSchemeSpec("client", Map.of()), "src/client.ts");
        for (final String scheme : List.of("partnerToken", "serviceToken", "ledgerKey", "adminLogin")) {
            assertTrue(client.contains("readonly " + scheme + "?: Authenticator;"), client);
            assertTrue(client.contains("['" + scheme + "', '" + scheme + "'],"), client);
        }
        assertTrue(client.contains("readonly authenticator?: Authenticator;"), client);
        assertTrue(client.contains("throw new TypeError('At least one credential is required');"), client);
    }

    @Test
    void anOperationResolvesTheCredentialOfTheSchemeItsSpecNames() throws IOException {
        final Path out = generateSchemeSpec("operation", Map.of());
        final String api = generatedFile(out, "src/api/default-api.ts");
        assertTrue(api.contains("this.resolveSchemeAuth(['partnerToken'])"), api);
        assertTrue(api.contains("async listInvoices(requestOptions?: RequestOptions)"), api);
        assertFalse(api.contains("options?.auth"), api);
        assertFalse(
                Files.exists(out.resolve("src/api/options/list-invoices-options.ts")),
                "an operation with no parameters of its own has no Options file");
    }
}
