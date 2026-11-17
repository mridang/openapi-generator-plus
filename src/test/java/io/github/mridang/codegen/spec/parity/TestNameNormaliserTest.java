package io.github.mridang.codegen.spec.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Every case is a test name that exists in the committed golden SDK of that language. */
class TestNameNormaliserTest {

  private static String[] row(String... values) {
    return values;
  }

  private static Stream<String[]> goldenNames() {
    return Stream.of(
        row(
            "java",
            "com.example.petstore.api.HeaderSelectorTest$IsJsonMimeTests",
            "IsJsonMimeTests",
            "shouldReturnTrueForUppercaseApplicationJson",
            "headerselector::shouldreturntrueforuppercaseapplicationjson"),
        row(
            "kotlin",
            "com.example.petstore.BaseApiTest",
            "com.example.petstore.BaseApiTest",
            "form-urlencoded body rejects a nested object field instead of sending its toString[jvm]",
            "baseapi::formurlencodedbodyrejectsanestedobjectfieldinsteadofsendingitstostring"),
        row(
            "kotlin",
            "com.example.petstore.auth.oauth.OAuth2ImplicitAuthenticatorTest",
            "x",
            "emptyAccessTokenIsTreatedAsUnset()[jvm]",
            "oauth2implicitauthenticator::emptyaccesstokenistreatedasunset"),
        row(
            "csharp",
            "Test.ApiExceptionTest",
            "Test collection for Test.ApiExceptionTest (id: cdef)",
            "Test.ApiExceptionTest.FromResponseKeepsAModeratelyNestedBody",
            "apierror::fromresponsekeepsamoderatelynestedbody"),
        row(
            "csharp",
            "Test.ObjectSerializerTestNonAsciiSerialization",
            "x",
            "Test.ObjectSerializerTestNonAsciiSerialization.RoundTrips(value: \"x\")",
            "objectserializer::roundtrips"),
        row(
            "php",
            "Tests.ApiExceptionTest",
            "every status-specific error keeps an optional cause",
            "every status-specific error keeps an optional cause with data set \"('Bad…')\"",
            "apierror::everystatusspecificerrorkeepsanoptionalcause"),
        row(
            "python",
            "test.api.test_pet_api.TestPetApi",
            "pytest",
            "test_find_pets_by_status_omitting_status_sends_no_status_in_query",
            "petapi::findpetsbystatusomittingstatussendsnostatusinquery"),
        row(
            "python",
            "test.test_default_api_client.TestHttpCompression",
            "pytest",
            "test_decompresses_the_response[zstd]",
            "defaultapiclient::decompressestheresponse"),
        row(
            "ruby",
            "Petstore::Client::DefaultApiClient",
            "Petstore::Client::DefaultApiClient",
            "test_0027_rejects multipart filename containing NUL byte",
            "defaultapiclient::rejectsmultipartfilenamecontainingnulbyte"),
        row(
            "ruby",
            "Petstore::Client::Auth::OAuth::OpenIdConnectAuthenticator",
            "x",
            "test_0007_obtains token after code exchange",
            "openidconnectauthenticator::obtainstokenaftercodeexchange"),
        row(
            "ruby",
            "Petstore::Client::Api::BaseApi",
            "x",
            "test_0081_reports a second 401 as Unauthorized after exactly one replay",
            "baseapi::reportsasecond401asunauthorizedafterexactlyonereplay"),
        row(
            "node",
            "OAuth2TokenManager extracts access token from response",
            "OAuth2TokenManager",
            "OAuth2TokenManager extracts access token from response",
            "oauth2tokenmanager::extractsaccesstokenfromresponse"),
        row(
            "dart",
            "test.server_configuration",
            "test.server_configuration",
            "ServerConfiguration resolves URL template using default variable values",
            "serverconfiguration::resolvesurltemplateusingdefaultvariablevalues"),
        row(
            "go",
            "petstore/pkg",
            "petstore/pkg",
            "TestSerializeValue_FormBooleanFalse",
            "serializevalue::formbooleanfalse"),
        row(
            "go",
            "petstore/test",
            "petstore/test",
            "TestOAuth2TokenManager_RefreshTokenEmptyStringPreservesExisting",
            "oauth2tokenmanager::refreshtokenemptystringpreservesexisting"),
        row(
            "rust",
            "petstore::base_api_test",
            "petstore::base_api_test",
            "test_a_second_401_is_reported_after_one_replay",
            "baseapi::asecond401isreportedafteronereplay"),
        row(
            "rust",
            "petstore",
            "petstore",
            "api::base_api::tests::test_retry_after_replaces_the_backoff_and_is_capped_at_sixty_seconds",
            "baseapi::retryafterreplacesthebackoffandiscappedatsixtyseconds"),
        row(
            "swift",
            "PetstoreClientTests.ValueSerializerTests",
            "TestResults",
            "testSerializeStyledFormArrayNoExplode()",
            "valueserializer::serializestyledformarraynoexplode"),
        row(
            "swift",
            "PetstoreClientTests.TransportOptionsTests",
            "TestResults",
            "testProxyErrorMasksTheUserinfo(url:message:)",
            "transportoptions::proxyerrormaskstheuserinfo"),
        row(
            "elixir",
            "Elixir.PetstoreClient.BearerAuthenticatorTest",
            "Elixir.PetstoreClient.BearerAuthenticatorTest",
            "test BearerAuthenticator mixed-case bearer prefix is stripped",
            "bearerauthenticator::mixedcasebearerprefixisstripped"));
  }

  @TestFactory
  Stream<DynamicTest> shouldMapEveryLanguagesNamingStyleToOneCanonicalKey() {
    return goldenNames()
        .map(
            r ->
                DynamicTest.dynamicTest(
                    r[0] + ": " + r[3],
                    () ->
                        assertThat(TestNameNormaliser.normalise(r[0], r[1], r[2], r[3]).canonical())
                            .isEqualTo(r[4])));
  }

  @Test
  void shouldGiveTheSameKeyToTheSameTestInEveryNamingStyle() {
    String expected = "valueserializer::pathbooleanfalsereturnsfalse";
    assertThat(
            TestNameNormaliser.normalise(
                    "java", "x.ValueSerializerTest$PathTests", "s", "pathBooleanFalseReturnsFalse")
                .canonical())
        .isEqualTo(expected);
    assertThat(
            TestNameNormaliser.normalise(
                    "python",
                    "test.test_value_serializer.TestValueSerializerPath",
                    "pytest",
                    "test_path_boolean_false_returns_false")
                .canonical())
        .isEqualTo(expected);
    assertThat(
            TestNameNormaliser.normalise(
                    "swift",
                    "T.ValueSerializerTests",
                    "TestResults",
                    "testPathBooleanFalseReturnsFalse()")
                .canonical())
        .isEqualTo(expected);
    assertThat(
            TestNameNormaliser.normalise(
                    "rust",
                    "petstore::value_serializer_test",
                    "x",
                    "test_path_boolean_false_returns_false")
                .canonical())
        .isEqualTo(expected);
    assertThat(
            TestNameNormaliser.normalise(
                    "elixir",
                    "Elixir.P.ValueSerializerTest",
                    "x",
                    "test path boolean false returns false")
                .canonical())
        .isEqualTo(expected);
  }

  @Test
  void shouldExposeSubjectAndTestSeparately() {
    TestNameNormaliser.Key key =
        TestNameNormaliser.normalise("go", "petstore/pkg", "x", "TestSerializeValue_FormBooleanFalse");
    assertThat(key.subject()).isEqualTo("serializevalue");
    assertThat(key.test()).isEqualTo("formbooleanfalse");
    assertThat(key).hasToString("serializevalue::formbooleanfalse");
  }

  @Test
  void shouldSplitCamelCaseAndAcronyms() {
    assertThat(TestNameNormaliser.words("OAuth2TokenManager"))
        .containsExactly("o", "auth2", "token", "manager");
    assertThat(TestNameNormaliser.words("parseHTTPResponse_v2"))
        .containsExactly("parse", "http", "response", "v2");
  }

  @Test
  void shouldRefuseALanguageItDoesNotKnow() {
    assertThatThrownBy(() -> TestNameNormaliser.normalise("cobol", "a", "b", "c"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cobol");
  }
}
