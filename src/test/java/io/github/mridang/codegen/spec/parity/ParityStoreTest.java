package io.github.mridang.codegen.spec.parity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("NullAway.Init")
class ParityStoreTest {

  @TempDir Path directory;

  private static ParitySummary summary(String language, long writtenAt) {
    return new ParitySummary(language, writtenAt, 1, new TreeSet<>(List.of("a::b")), List.of());
  }

  @Test
  void shouldReadBackWhatWasWritten() throws IOException {
    ParityStore store = new ParityStore(directory.resolve("test-parity"));
    store.write(summary("go", 10L));
    store.write(summary("ruby", 20L));

    Map<String, ParitySummary> read = store.readSince(0L);

    assertThat(read).containsOnlyKeys("go", "ruby");
    assertThat(Files.exists(directory.resolve("test-parity/go.json"))).isTrue();
  }

  @Test
  void shouldIgnoreSummariesOfAnEarlierRun() throws IOException {
    ParityStore store = new ParityStore(directory);
    store.write(summary("go", 10L));
    store.write(summary("ruby", 20L));

    assertThat(store.readSince(15L)).containsOnlyKeys("ruby");
  }

  @Test
  void shouldReadNothingFromAFolderThatDoesNotExist() throws IOException {
    assertThat(new ParityStore(directory.resolve("absent")).readSince(0L)).isEmpty();
    assertThat(new ParityStore(directory.resolve("absent")).running()).isEmpty();
  }

  @Test
  void shouldListALanguageAsRunningUntilItsSummaryIsWritten() throws IOException {
    ParityStore store = new ParityStore(directory);
    store.markRunning("go");
    store.markRunning("ruby");

    assertThat(store.running()).containsExactly("go", "ruby");

    store.write(summary("go", 1L));
    store.clearRunning("ruby");

    assertThat(store.running()).isEmpty();
  }

  @Test
  void shouldKeepTheStoreUnderTargetOfTheCheckout() {
    assertThat(ParityStore.defaultStore().directory().toString())
        .endsWith("target/test-parity");
    assertThat(ParityStore.jvmStartTime()).isPositive();
  }
}
