package io.github.mridang.codegen.spec.parity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BehaviourIdTest {

  private static Map<String, String> names() {
    Map<String, String> names = new LinkedHashMap<>();
    names.put("java", "bhvTransport014RetriesOn503");
    names.put("kotlin", "bhvTransport014RetriesOn503");
    names.put("csharp", "BhvTransport014RetriesOn503");
    names.put("php", "test_bhv_transport_014_retries_on_503");
    names.put("python", "test_bhv_transport_014_retries_on_503");
    names.put("ruby", "test_0007_bhv transport 014 retries on 503");
    names.put("node", "bhvTransport014RetriesOn503");
    names.put("dart", "bhv transport 014 retries on 503");
    names.put("go", "TestBhvTransport014_RetriesOn503");
    names.put("rust", "transport::test_bhv_transport_014_retries_on_503");
    names.put("swift", "testBhvTransport014RetriesOn503()");
    names.put("elixir", "test bhv transport 014 retries on 503");
    return names;
  }

  @Test
  void shouldFindTheIdInEveryNamingStyle() {
    names()
        .forEach(
            (language, name) ->
                assertThat(BehaviourId.extract(name)).as(language).contains("transport014"));
  }

  @Test
  void shouldFindTheIdWhenTheFrameworkAddsParametersAndPrefixes() {
    names()
        .forEach(
            (language, name) ->
                assertThat(BehaviourId.extract("Suite > " + name + " [1] (String)"))
                    .as(language)
                    .contains("transport014"));
  }

  @Test
  void shouldKeepAreasThatLookLikeWordsWithDigitsApart() {
    assertThat(BehaviourId.extract("bhvAuthoauth031RefreshesToken")).contains("authoauth031");
    assertThat(BehaviourId.extract("test_bhv_authoauth_031_refreshes_token"))
        .contains("authoauth031");
    assertThat(BehaviourId.extract("bhvAuth031Oauth2Refreshes")).contains("auth031");
    assertThat(BehaviourId.extract("bhvRetries007")).contains("retries007");
  }

  @Test
  void shouldTakeTheFirstIdOfAName() {
    assertThat(BehaviourId.extract("bhvTransport014AndBhvRetries007")).contains("transport014");
  }

  @Test
  void shouldReadNoIdWhenThereAreNotExactlyThreeDigits() {
    assertThat(BehaviourId.extract("bhvTransport14RetriesOn503")).isEmpty();
    assertThat(BehaviourId.extract("bhvTransport0145Retries")).isEmpty();
    assertThat(BehaviourId.extract("bhv transport 014 503 retries")).isEmpty();
    assertThat(BehaviourId.extract("retriesOn503")).isEmpty();
  }
}
