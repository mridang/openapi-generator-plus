package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

/** Covers how declared error responses map to status classes and schema classes. */
class ErrorResponsesTest {

    private static ErrorResponses.Declared d(String status, @Nullable String model) {
        return new ErrorResponses.Declared(status, model);
    }

    @Test
    void everyKnownStatusHasItsOwnStem() {
        assertEquals("NotFound", ErrorResponses.statusStem("404"));
        assertEquals("PaymentRequired", ErrorResponses.statusStem("402"));
        assertEquals("MethodNotAllowed", ErrorResponses.statusStem("405"));
        assertEquals("RequestTimeout", ErrorResponses.statusStem("408"));
        assertEquals("Gone", ErrorResponses.statusStem("410"));
        assertEquals("PayloadTooLarge", ErrorResponses.statusStem("413"));
        assertEquals("UnsupportedMediaType", ErrorResponses.statusStem("415"));
        assertEquals("Locked", ErrorResponses.statusStem("423"));
        assertEquals("NotImplemented", ErrorResponses.statusStem("501"));
        assertEquals("BadGateway", ErrorResponses.statusStem("502"));
        assertEquals("ServiceUnavailable", ErrorResponses.statusStem("503"));
        assertEquals("GatewayTimeout", ErrorResponses.statusStem("504"));
    }

    @Test
    void unknownStatusesAndRangesFallBackToTheFamily() {
        assertEquals("Client", ErrorResponses.statusStem("418"));
        assertEquals("Client", ErrorResponses.statusStem("4XX"));
        assertEquals("Client", ErrorResponses.statusStem("4xx"));
        assertEquals("Server", ErrorResponses.statusStem("599"));
        assertEquals("Server", ErrorResponses.statusStem("5XX"));
        assertEquals("Api", ErrorResponses.statusStem("default"));
        assertEquals("Api", ErrorResponses.statusStem("0"));
    }

    @Test
    void parentsFollowTheFamilies() {
        assertEquals("Client", ErrorResponses.parentStem("NotFound"));
        assertEquals("Client", ErrorResponses.parentStem("Locked"));
        assertEquals("Server", ErrorResponses.parentStem("BadGateway"));
        assertEquals("Server", ErrorResponses.parentStem("InternalServerError"));
        assertEquals("Api", ErrorResponses.parentStem("Client"));
        assertEquals("Api", ErrorResponses.parentStem("Server"));
        assertEquals("Api", ErrorResponses.parentStem("Api"));
    }

    @Test
    void commonStemIsTheLowestSharedAncestor() {
        assertEquals("Conflict", ErrorResponses.commonStem(List.of("Conflict", "Conflict")));
        assertEquals("Client", ErrorResponses.commonStem(List.of("Conflict", "Locked")));
        assertEquals("Client", ErrorResponses.commonStem(List.of("NotFound", "Client")));
        assertEquals("Api", ErrorResponses.commonStem(List.of("NotFound", "BadGateway")));
        assertEquals("Api", ErrorResponses.commonStem(List.of("Client", "Api")));
        assertEquals("Api", ErrorResponses.commonStem(List.of()));
    }

    @Test
    void classNamesCarryTheLanguageSuffix() {
        assertEquals("NotFoundException", ErrorResponses.statusClassName("NotFound", "Exception"));
        assertEquals("NotFoundError", ErrorResponses.statusClassName("NotFound", "Error"));
        assertEquals(
                "InternalServerErrorException",
                ErrorResponses.statusClassName("InternalServerError", "Exception"));
        assertEquals(
                "InternalServerError",
                ErrorResponses.statusClassName("InternalServerError", "Error"));
        assertEquals("ApiError", ErrorResponses.statusClassName("Api", "Error"));
        assertEquals(
                "ProjectNotFoundException",
                ErrorResponses.schemaClassName("ProjectNotFound", "Exception"));
    }

    @Test
    void successAndRedirectResponsesAreNotErrors() {
        assertFalse(ErrorResponses.isErrorStatus("200"));
        assertFalse(ErrorResponses.isErrorStatus("2XX"));
        assertFalse(ErrorResponses.isErrorStatus("302"));
        assertTrue(ErrorResponses.isErrorStatus("404"));
        assertTrue(ErrorResponses.isErrorStatus("5XX"));
        assertTrue(ErrorResponses.isErrorStatus("default"));
        assertTrue(ErrorResponses.isErrorStatus("0"));
    }

    @Test
    void operationRowsKeepDeclaredOrderWithTheDefaultLast() {
        final List<Map<String, Object>> rows =
                ErrorResponses.operationRows(
                        List.of(
                                d("default", "Fallback"),
                                d("200", "Ok"),
                                d("404", "Missing"),
                                d("4XX", null),
                                d("404", "Ignored")),
                        "Exception");
        assertEquals(3, rows.size());
        assertEquals("404", rows.get(0).get("status"));
        assertEquals("Missing", rows.get(0).get("schemaName"));
        assertEquals("MissingException", rows.get(0).get("schemaClassName"));
        assertEquals("NotFoundException", rows.get(0).get("statusClassName"));
        assertEquals(true, rows.get(0).get("isFirst"));
        assertEquals("4XX", rows.get(1).get("status"));
        assertEquals(true, rows.get(1).get("isRange"));
        assertEquals(false, rows.get(1).get("hasSchema"));
        assertNull(rows.get(1).get("schemaName"));
        assertEquals("ClientException", rows.get(1).get("statusClassName"));
        assertEquals("default", rows.get(2).get("status"));
        assertEquals(true, rows.get(2).get("isDefault"));
        assertEquals("ApiException", rows.get(2).get("statusClassName"));
        assertEquals(true, rows.get(2).get("isLast"));
    }

    @Test
    void anOperationWithoutErrorsHasNoRows() {
        assertTrue(ErrorResponses.operationRows(List.of(d("200", "Ok")), "Error").isEmpty());
    }

    @Test
    void distinctSchemasMergeStatusesAndPickTheCommonParent() {
        final List<Map<String, Object>> first =
                ErrorResponses.operationRows(
                        List.of(d("404", "ProjectGone"), d("409", "State"), d("423", "State")),
                        "Exception");
        final List<Map<String, Object>> second =
                ErrorResponses.operationRows(
                        List.of(d("404", "MemberGone"), d("default", "Other")), "Exception");
        final List<Map<String, Object>> schemas =
                ErrorResponses.distinctSchemas(List.of(first, second), "Exception");
        assertEquals(4, schemas.size());
        assertEquals("ProjectGone", schemas.get(0).get("schemaName"));
        assertEquals("ProjectGoneException", schemas.get(0).get("className"));
        assertEquals("NotFoundException", schemas.get(0).get("parentClassName"));
        assertEquals("State", schemas.get(1).get("schemaName"));
        assertEquals(List.of("409", "423"), schemas.get(1).get("statuses"));
        assertEquals(true, schemas.get(1).get("hasMultipleStatuses"));
        assertEquals("ClientException", schemas.get(1).get("parentClassName"));
        assertEquals("NotFoundException", schemas.get(2).get("parentClassName"));
        assertEquals("ApiException", schemas.get(3).get("parentClassName"));
        assertEquals(true, schemas.get(3).get("isLast"));
    }

    @Test
    void aSchemaSharedByTwoOperationsAppearsOnce() {
        final List<Map<String, Object>> a =
                ErrorResponses.operationRows(List.of(d("404", "Shared")), "Error");
        final List<Map<String, Object>> b =
                ErrorResponses.operationRows(List.of(d("404", "Shared")), "Error");
        assertEquals(1, ErrorResponses.distinctSchemas(List.of(a, b), "Error").size());
    }

    @Test
    void sampleStatusIsAConcreteStatusForEveryKey() {
        assertEquals(404, ErrorResponses.sampleStatus("404"));
        assertEquals(400, ErrorResponses.sampleStatus("4XX"));
        assertEquals(500, ErrorResponses.sampleStatus("5XX"));
        assertEquals(400, ErrorResponses.sampleStatus("default"));
    }

    @Test
    void aSchemaDeclaredUnderAFamilyOrSeveralStatusesTakesAStatusArgument() {
        final List<Map<String, Object>> schemas =
                ErrorResponses.distinctSchemas(
                        List.of(
                                ErrorResponses.operationRows(
                                        List.of(d("404", "Gone"), d("4XX", "Wide"), d("default", "Other")),
                                        "Error")),
                        "Error");
        assertEquals(false, schemas.get(0).get("parentIsFamily"));
        assertEquals(true, schemas.get(1).get("parentIsFamily"));
        assertEquals(true, schemas.get(2).get("parentIsFamily"));
        assertEquals(404, schemas.get(0).get("sampleStatus"));
    }

    @Test
    void aSchemaSaysWhichFamilyItsParentStatusClassBelongsTo() {
        final List<Map<String, Object>> schemas =
                ErrorResponses.distinctSchemas(
                        List.of(
                                ErrorResponses.operationRows(
                                        List.of(
                                                d("404", "Gone"),
                                                d("503", "Down"),
                                                d("4XX", "Wide"),
                                                d("5XX", "Broken"),
                                                d("default", "Other")),
                                        "Error")),
                        "Error");
        assertEquals(true, schemas.get(0).get("parentUnderClient"));
        assertEquals(false, schemas.get(0).get("parentUnderServer"));
        assertEquals(true, schemas.get(1).get("parentUnderServer"));
        assertEquals(true, schemas.get(2).get("parentIsClient"));
        assertEquals(true, schemas.get(3).get("parentIsServer"));
        assertEquals(true, schemas.get(4).get("parentIsApi"));
        assertEquals(false, schemas.get(4).get("parentUnderClient"));
    }
}
