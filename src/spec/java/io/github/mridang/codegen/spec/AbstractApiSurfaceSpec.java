package io.github.mridang.codegen.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

/**
 * The public API guard of one generated SDK (D180, issue 122): a snapshot of its public
 * signatures, a reachability check and a leak check, all read from one extraction run inside the
 * language's runtime container.
 *
 * <p>The extraction is a script under {@code src/spec/resources/api-surface/extractors/}, copied
 * into the container as {@code /tmp/api-surface/<name>} and run by the commands of {@link
 * #getSurfaceCommands()}. It reads the output of the language's own documentation tool or
 * compiler where one exists (javap, the C# XML documentation file, the TypeScript declarations,
 * {@code go doc}, the dartdoc index, ...) and otherwise the language's reflection. It prints one
 * record per line, each starting with {@code @@} and a letter; every other line is ignored:
 *
 * <ul>
 *   <li>{@code @@S <signature>}: one public signature, normalised to a single line;
 *   <li>{@code @@T <type>}: a public type the SDK declares;
 *   <li>{@code @@E <type>}: a type reachable from the package entry point (the barrel, the package,
 *       the namespace or the crate root, in the language's sense);
 *   <li>{@code @@I <type>}: a type the language marks internal that is nevertheless exported.
 * </ul>
 *
 * <p>Three checks follow. The sorted, de-duplicated {@code @@S} lines must equal the committed
 * snapshot {@code src/spec/resources/api-surface/<lang>.txt}; a difference fails with the removed
 * lines (breaking) and the added lines (additive) listed apart, and {@code
 * -DupdateApiSurface=true} rewrites the snapshot instead, as goldens are regenerated. Every {@code
 * @@T} must also be an {@code @@E}, and no {@code @@I} may appear. No {@code @@S} line may name a type of
 * a third-party library ({@link #getThirdPartyPatterns()}), unless the line is listed with its
 * reason in {@code src/spec/resources/api-surface/<lang>.leaks.txt} ({@code <signature> | <reason>}).
 * A type that is unreachable or internal-but-exported for a stated reason is listed the same way
 * in {@code <lang>.reach.txt}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractApiSurfaceSpec extends AbstractIntegrationSpec {

  private static final Path SURFACE_DIR = Path.of("src/spec/resources/api-surface");

  /** The extractor scripts under {@code api-surface/extractors/} this language runs. */
  protected abstract List<String> getExtractorScripts();

  /** The commands, run in order from the SDK root, that print the records. */
  protected abstract String[] getSurfaceCommands();

  /**
   * The patterns of third-party type names that must not appear in a public signature: the HTTP
   * client, the JSON library, the logging framework and anything else the SDK depends on.
   */
  protected abstract List<Pattern> getThirdPartyPatterns();

  /** The language key used for the snapshot file names. */
  protected String surfaceName() {
    return getGeneratorName().replace("-plus", "");
  }

  @Override
  protected final String[] getBuildCommands() {
    return getSurfaceCommands();
  }

  /** The records of one extraction run, read once per test instance. */
  @javax.annotation.Nullable private Surface surface;

  private synchronized Surface surface() throws IOException {
    Surface cached = surface;
    if (cached == null) {
      GenericContainer<?> container = SharedRuntimeContainer.getOrCreate(this, tempOutputDir);
      try {
        container.execInContainer("mkdir", "-p", "/tmp/api-surface");
      } catch (IOException e) {
        throw new java.io.UncheckedIOException(e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      for (String script : getExtractorScripts()) {
        container.copyFileToContainer(
            MountableFile.forHostPath(SURFACE_DIR.resolve("extractors").resolve(script)),
            "/tmp/api-surface/" + script);
      }
      ExecResult result = executeInRuntimeContainer(getSurfaceCommands());
      assertThat(result.isSuccess())
          .withFailMessage("The API surface extraction failed:\n%s", result.output())
          .isTrue();
      cached = Surface.parse(result.output());
      surface = cached;
      assertThat(cached.signatures)
          .withFailMessage("The API surface extraction printed no signature:\n%s", result.output())
          .isNotEmpty();
    }
    return cached;
  }

  @Test
  void publicSignaturesMatchTheSnapshot() throws IOException {
    Path snapshot = SURFACE_DIR.resolve(surfaceName() + ".txt");
    List<String> actual = new ArrayList<>(surface().signatures);
    if (Boolean.getBoolean("updateApiSurface")) {
      Files.createDirectories(SURFACE_DIR);
      Files.write(snapshot, actual, StandardCharsets.UTF_8);
      return;
    }
    assertThat(Files.exists(snapshot))
        .withFailMessage(
            "No API surface snapshot at %s; run this spec with -DupdateApiSurface=true", snapshot)
        .isTrue();
    TreeSet<String> expected = new TreeSet<>(Files.readAllLines(snapshot, StandardCharsets.UTF_8));
    expected.remove("");
    TreeSet<String> removed = new TreeSet<>(expected);
    removed.removeAll(actual);
    TreeSet<String> added = new TreeSet<>(actual);
    added.removeAll(expected);
    assertThat(removed.isEmpty() && added.isEmpty())
        .withFailMessage(
            "The public API of the %s SDK changed (rerun with -DupdateApiSurface=true to accept)."
                + "%nBreaking, removed or changed (%d):%n%s%nAdditive, new (%d):%n%s",
            surfaceName(),
            removed.size(),
            String.join(System.lineSeparator(), removed),
            added.size(),
            String.join(System.lineSeparator(), added))
        .isTrue();
  }

  @Test
  void everyPublicTypeIsReachableAndNothingInternalIsExported() throws IOException {
    Surface s = surface();
    Map<String, String> allowed = allowList(surfaceName() + ".reach.txt");
    TreeSet<String> unreachable = new TreeSet<>(s.types);
    unreachable.removeAll(s.exported);
    unreachable.removeAll(allowed.keySet());
    assertThat(unreachable)
        .withFailMessage(
            "Public types not reachable from the package entry point:%n%s",
            String.join(System.lineSeparator(), unreachable))
        .isEmpty();
    TreeSet<String> internal = new TreeSet<>(s.internal);
    internal.removeAll(allowed.keySet());
    assertThat(internal)
        .withFailMessage(
            "Types marked internal but exported:%n%s",
            String.join(System.lineSeparator(), internal))
        .isEmpty();
  }

  /** Reads an allow list of {@code <entry> | <reason>} lines; blank and {@code #} lines are skipped. */
  private static Map<String, String> allowList(String name) throws IOException {
    Map<String, String> allowed = new LinkedHashMap<>();
    Path file = SURFACE_DIR.resolve(name);
    if (Files.exists(file)) {
      for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        int bar = line.lastIndexOf(" | ");
        if (!line.isBlank() && !line.startsWith("#") && bar > 0) {
          allowed.put(line.substring(0, bar), line.substring(bar + 3));
        }
      }
    }
    return allowed;
  }

  @Test
  void noPublicSignatureNamesAThirdPartyType() throws IOException {
    Map<String, String> allowed = allowList(surfaceName() + ".leaks.txt");
    List<String> leaks =
        surface().signatures.stream()
            .filter(sig -> !allowed.containsKey(sig))
            .filter(sig -> getThirdPartyPatterns().stream().anyMatch(p -> p.matcher(sig).find()))
            .collect(Collectors.toList());
    assertThat(leaks)
        .withFailMessage(
            "Public signatures that expose a third-party type:%n%s",
            String.join(System.lineSeparator(), leaks))
        .isEmpty();
  }

  /** The parsed records of one extraction. */
  static final class Surface {
    final TreeSet<String> signatures = new TreeSet<>();
    final TreeSet<String> types = new TreeSet<>();
    final TreeSet<String> exported = new TreeSet<>();
    final TreeSet<String> internal = new TreeSet<>();

    static Surface parse(String output) {
      Surface s = new Surface();
      for (String raw : (Iterable<String>) output.lines()::iterator) {
        if (raw.length() < 5 || !raw.startsWith("@@") || raw.charAt(3) != ' ') {
          continue;
        }
        String value = raw.substring(4).strip();
        if (value.isEmpty()) {
          continue;
        }
        char kind = raw.charAt(2);
        if (kind == 'S') {
          s.signatures.add(value);
        } else if (kind == 'T') {
          s.types.add(value);
        } else if (kind == 'E') {
          s.exported.add(value);
        } else if (kind == 'I') {
          s.internal.add(value);
        }
      }
      return s;
    }
  }
}
