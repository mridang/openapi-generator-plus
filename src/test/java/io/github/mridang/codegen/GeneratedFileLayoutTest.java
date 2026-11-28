package io.github.mridang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.FileRoleNormaliser.Classified;
import io.github.mridang.codegen.FileRoleNormaliser.Kind;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Keeps the file layout of the twelve generated SDKs consistent. It reads the committed goldens
 * only (no containers), turns every file into a role with {@link FileRoleNormaliser} and compares
 * the languages: a core role every language shares may not be missing, a file that fits no role is
 * extraneous, and one role may not be spelled twice in one language. Every exception below says
 * why it exists; add one only for a real language difference, never to make a run pass.
 *
 * <p>It holds every {@code generated-<language>} lock in read mode so the generator tests, which
 * rewrite the goldens under the write lock, never run while it reads them.
 */
@ResourceLock(value = "generated-csharp", mode = ResourceAccessMode.READ)
@ResourceLock(value = "generated-dart", mode = ResourceAccessMode.READ)
@ResourceLock(value = "generated-elixir", mode = ResourceAccessMode.READ)
@ResourceLock(value = "generated-go", mode = ResourceAccessMode.READ)
@ResourceLock(value = "generated-java", mode = ResourceAccessMode.READ)
@ResourceLock(value = "generated-kotlin", mode = ResourceAccessMode.READ)
@ResourceLock(value = "generated-node", mode = ResourceAccessMode.READ)
@ResourceLock(value = "generated-php", mode = ResourceAccessMode.READ)
@ResourceLock(value = "generated-python", mode = ResourceAccessMode.READ)
@ResourceLock(value = "generated-ruby", mode = ResourceAccessMode.READ)
@ResourceLock(value = "generated-rust", mode = ResourceAccessMode.READ)
@ResourceLock(value = "generated-swift", mode = ResourceAccessMode.READ)
class GeneratedFileLayoutTest {

  private static final Path GOLDENS = Paths.get("src/spec/resources/generated");
  private static final File SPEC = new File("src/spec/resources/specs/petstore/openapi.yaml");

  /** A role is core when at least this many of the twelve languages have it. */
  private static final int CORE_THRESHOLD = 10;

  private static final Set<String> EXCEPTION_SUFFIX =
      new HashSet<>(Arrays.asList("csharp", "dart", "java", "kotlin", "php", "python"));

  /** A core role a language does not have as a file of its own, and why. */
  private static final Map<String, Map<String, String>> ALLOWED_MISSING = new LinkedHashMap<>();

  /** A role only a few languages have, and why this language needs it. */
  private static final Map<String, Map<String, String>> ALLOWED_EXTRA = new LinkedHashMap<>();

  /** A role that may be spelled by two files of one language, and why. */
  private static final Map<String, Map<String, String>> ALLOWED_DUPLICATE = new LinkedHashMap<>();

  /** A file that fits no role but is allowed, by path inside the language folder, and why. */
  private static final Map<String, Map<String, String>> ALLOWED_FILE = new LinkedHashMap<>();

  private static void allow(
      Map<String, Map<String, String>> table, String language, String key, String reason) {
    table.computeIfAbsent(language, k -> new LinkedHashMap<>()).put(key, reason);
  }

  static {
    allow(
        ALLOWED_MISSING,
        "python",
        "error:api",
        "ApiException lives in errors/__init__.py: it subclasses OpenAPIException, which has to be"
            + " defined in the same module to avoid a circular import");
    allow(
        ALLOWED_MISSING,
        "python",
        "error:openapi",
        "OpenAPIException lives in errors/__init__.py as the base every other error imports");
    allow(
        ALLOWED_MISSING,
        "php",
        "apistreamresponse",
        "the PHP transport returns the ByteStream directly, because a PSR-7 body carrier already"
            + " holds the status, headers and open body");
    allow(
        ALLOWED_MISSING,
        "php",
        "jsonlinesstream",
        "a PHP JSON Lines stream is a plain Generator of items, so there is no class to hold");
    allow(
        ALLOWED_MISSING,
        "elixir",
        "streamoptions",
        "the stream settings are a keyword list in the opts argument, as for every other Elixir call");
    allow(
        ALLOWED_MISSING,
        "go",
        "streamoptions",
        "the stream settings are RequestOption values (WithIdleTimeout and friends), as for every other Go call setting");
    allow(
        ALLOWED_MISSING,
        "elixir",
        "linkheader",
        "the RFC 8288 Link header parser is private to the Paginator module in Elixir");
    allow(
        ALLOWED_MISSING,
        "node",
        "page",
        "the Page type lives in paginator.ts beside the Paginator that yields it");
    allow(
        ALLOWED_MISSING,
        "rust",
        "page",
        "the Page type lives in paginator.rs beside the Paginator that yields it");
    allow(
        ALLOWED_MISSING,
        "rust",
        "baseauthenticator",
        "rust has no class inheritance, so the shared authenticator behaviour is a trait default"
            + " in authenticator.rs");

    allow(
        ALLOWED_DUPLICATE,
        "kotlin",
        "tracecontextutil",
        "Kotlin multiplatform: an expect declaration in src/main and its actual in src/jvmMain");
    allow(
        ALLOWED_DUPLICATE,
        "csharp",
        "client",
        "the mock seam: IClient is the interface a fake implements, declared in its own file beside"
            + " the Client class that implements it");

    allow(ALLOWED_EXTRA, "elixir", "streambody", "the process that owns an open response body, because Req can be read only by the process that sent the request");
    allow(ALLOWED_EXTRA, "ruby", "streambody", "the open response body fed by the producer thread that runs the Faraday request");
    allow(
        ALLOWED_EXTRA,
        "php",
        "streamingapiclient",
        "PHP 8.3 interfaces cannot carry a default method, so the transport that can stream is a"
            + " second interface that the SDK checks at call time (D118)");
    allow(ALLOWED_EXTRA, "php", "rawstreamresponse", "the internal pair of status, headers and open body that a PHP transport hands to the ByteStream");
    allow(ALLOWED_EXTRA, "dart", "bodysourcefilesio", "dart:io file reading of a body source, chosen by conditional import");
    allow(ALLOWED_EXTRA, "dart", "bodysourcefilesweb", "the browser stand-in for file reading of a body source, chosen by conditional import");
    allow(ALLOWED_EXTRA, "swift", "streamtransfer", "the URLSession delegate that feeds the AsyncThrowingStream, because AsyncBytes is absent on Linux");
    allow(ALLOWED_EXTRA, "dart", "retryafter", "the Retry-After parser shared by the retry loop and the server-sent events reconnect");
    allow(ALLOWED_EXTRA, "go", "doc", "a go package description can only live in a package comment file");
    allow(
        ALLOWED_EXTRA,
        "php",
        "wireformat",
        "the attribute that names the wire format of a uri or ip property is its own class in php");
    for (String language : Arrays.asList("csharp", "dart", "elixir", "java", "php", "ruby", "swift")) {
      allow(
          ALLOWED_EXTRA,
          language,
          "callinfo",
          "the call hook's request description is its own type where the other languages declare it inline");
      allow(
          ALLOWED_EXTRA,
          language,
          "calloutcome",
          "the call hook's result description is its own type where the other languages declare it inline");
    }
    for (String language : Arrays.asList("csharp", "java", "php")) {
      allow(
          ALLOWED_EXTRA,
          language,
          "callobserver",
          "the call hook's observer is its own interface where the other languages return a closure or a state");
    }
    allow(
        ALLOWED_EXTRA,
        "ruby",
        "allofauthenticator",
        "Ruby declares one class per file and the requirement composite must load after the authenticator base class, so it cannot sit in the base API file as in the other languages");
    allow(
        ALLOWED_EXTRA,
        "ruby",
        "allofhttpawareauthenticator",
        "the token-aware variant of the requirement composite, in its own file for the same reason");
    allow(ALLOWED_EXTRA, "php", "callhookchain", "PHP chains hooks in one class, as it does for authenticators");
    allow(ALLOWED_EXTRA, "csharp", "chainedcallhook", "C# chains hooks in one class behind ICallHook.Chain");
    for (String language : Arrays.asList("csharp", "java", "kotlin", "node", "php", "python", "rust")) {
      allow(
          ALLOWED_EXTRA,
          language,
          "opentelemetrycallhook",
          "the optional OpenTelemetry adapter, shipped only where the language has a stable API package");
    }
    allow(ALLOWED_EXTRA, "go", "otelhook", "the optional OpenTelemetry adapter, its own Go package");
    allow(ALLOWED_EXTRA, "dart", "abstractapiclient", "dart base class the io and web clients extend");
    allow(ALLOWED_EXTRA, "node", "abstractapiclient", "TypeScript base class the node and web clients extend");
    allow(ALLOWED_EXTRA, "php", "abstractapiclient", "PHP base class the PSR-18 client extends");
    allow(ALLOWED_EXTRA, "swift", "anycodable", "Swift has no dynamic JSON value type, so one is shipped");
    allow(ALLOWED_EXTRA, "elixir", "apimodule", "the macro every generated API module uses");
    allow(ALLOWED_EXTRA, "node", "brand", "TypeScript nominal-type brand helper");
    allow(ALLOWED_EXTRA, "php", "clientlifecycle", "PHP closes the transport through an explicit lifecycle");
    allow(ALLOWED_EXTRA, "php", "compositeauthenticator", "PHP chains authenticators in one composite");
    allow(ALLOWED_EXTRA, "php", "noauth", "PHP null object for an unauthenticated client");
    allow(ALLOWED_EXTRA, "php", "configurationbuilder", "PHP has no named arguments for the options, so a builder");
    allow(ALLOWED_EXTRA, "php", "transportoptionsbuilder", "PHP builder for the transport options");
    for (String language : Arrays.asList("go", "node", "python", "rust")) {
      allow(
          ALLOWED_EXTRA,
          language,
          "credentials",
          "credential value types for the authenticators, which other languages declare inline");
    }
    allow(ALLOWED_EXTRA, "rust", "datetime", "rust date-time (de)serialisation helpers");
    allow(ALLOWED_EXTRA, "node", "deepinput", "TypeScript helper that deep-freezes configuration input");
    allow(ALLOWED_EXTRA, "dart", "defaultapiclientio", "dart:io implementation chosen by conditional import");
    allow(ALLOWED_EXTRA, "dart", "defaultapiclientweb", "dart:html implementation chosen by conditional import");
    allow(ALLOWED_EXTRA, "node", "defaultapiclientweb", "browser implementation chosen by the package exports");
    for (String name :
        Arrays.asList(
            "dsawareobjectnormalizer", "dsmapnormalizer", "dssetnormalizer", "dsvectornormalizer",
            "decimalnormalizer", "durationnormalizer")) {
      allow(ALLOWED_EXTRA, "php", name, "Symfony Serializer normaliser for a PHP type it cannot handle");
    }
    allow(ALLOWED_EXTRA, "python", "duration", "python has no ISO-8601 duration type");
    allow(ALLOWED_EXTRA, "dart", "iso8601duration", "dart has no ISO-8601 duration type");
    allow(ALLOWED_EXTRA, "swift", "iso8601duration", "swift has no ISO-8601 duration type");
    allow(ALLOWED_EXTRA, "go", "iso8601", "go has no ISO-8601 duration type");
    allow(ALLOWED_EXTRA, "php", "preciseduration", "PHP DateInterval cannot hold an ISO-8601 duration");
    allow(ALLOWED_EXTRA, "rust", "protoduration", "rust duration parsing for the proto3 JSON form");
    allow(ALLOWED_EXTRA, "dart", "environment", "dart platform detection used by the conditional imports");
    allow(ALLOWED_EXTRA, "dart", "environmentio", "dart:io platform detection");
    allow(ALLOWED_EXTRA, "dart", "environmentweb", "dart:html platform detection");
    allow(ALLOWED_EXTRA, "csharp", "error:errorfactory", "csharp declares the factory that turns a declared error response into its typed error as its own type");
    allow(ALLOWED_EXTRA, "java", "error:errorfactory", "java declares the factory that turns a declared error response into its typed error as its own type");
    allow(ALLOWED_EXTRA, "kotlin", "error:errorfactory", "kotlin declares the factory that turns a declared error response into its typed error as its own type");
    allow(ALLOWED_EXTRA, "swift", "error:errorfactory", "swift declares the factory that turns a declared error response into its typed error as its own type");
    allow(ALLOWED_EXTRA, "rust", "error:configuration", "rust error for an invalid client configuration");
    allow(ALLOWED_EXTRA, "swift", "error:configuration", "swift error for an invalid client configuration");
    allow(ALLOWED_EXTRA, "rust", "error:oauth2authorizationcode", "rust error of the authorization-code flow");
    allow(ALLOWED_EXTRA, "swift", "error:oauth2authorizationcode", "swift error of the authorization-code flow");
    allow(ALLOWED_EXTRA, "go", "errors", "go declares the shared error helpers next to the errors package");
    allow(ALLOWED_EXTRA, "rust", "formurlencode", "rust has no form-encoding in the standard library");
    allow(ALLOWED_EXTRA, "kotlin", "httpclientfactory", "kotlin JVM source set creates the platform HTTP client");
    allow(ALLOWED_EXTRA, "swift", "indirect", "swift indirect-box wrapper for recursive models");
    allow(ALLOWED_EXTRA, "rust", "jsonvalue", "rust dynamic JSON value type");
    allow(ALLOWED_EXTRA, "go", "pathutils", "go path-template helpers");
    allow(ALLOWED_EXTRA, "php", "psr18apiclient", "the PSR-18 client implementation");
    allow(ALLOWED_EXTRA, "php", "encodedpart", "PHP multipart part carrying its Encoding Object headers");
    allow(ALLOWED_EXTRA, "php", "rawhttpresponse", "PHP wrapper for the PSR-7 response");
    allow(ALLOWED_EXTRA, "go", "requestoption", "go functional request options");
    for (String language : Arrays.asList("java", "php", "ruby")) {
      allow(ALLOWED_EXTRA, language, "servervariable", "server URL variable type of a server entry");
    }
    allow(ALLOWED_EXTRA, "python", "types", "python shared type aliases");
    allow(ALLOWED_EXTRA, "ruby", "types", "ruby shared type aliases for the RBS signatures");
    allow(ALLOWED_EXTRA, "ruby", "version", "ruby gem version constant");
    for (String language : Arrays.asList("java", "kotlin", "php")) {
      allow(
          ALLOWED_EXTRA,
          language,
          "clientinterface",
          "the mock seam: ClientInterface is the top-level client a test fakes, beside Client");
      for (String group : Arrays.asList("pet", "problems", "store")) {
        allow(
            ALLOWED_EXTRA,
            language,
            group + "apiinterface",
            "the mock seam: the interface of the " + group + " API group a test fakes");
      }
    }
    allow(
        ALLOWED_EXTRA,
        "swift",
        "clientprotocol",
        "the mock seam: ClientProtocol is the top-level client a test fakes, beside Client");
    for (String group : Arrays.asList("pet", "problems", "store")) {
      allow(
          ALLOWED_EXTRA,
          "swift",
          group + "apiprotocol",
          "the mock seam: the protocol of the " + group + " API group a test fakes");
    }

    allow(ALLOWED_FILE, "elixir", "lib/petstore_client/models/models_module.ex", "namespace module of the models");
    allow(ALLOWED_FILE, "go", "pkg/models/date_time.go", "go date-time model helper");
    allow(ALLOWED_EXTRA, "go", "protoduration", "go protobuf-JSON duration codec, internal to the module");
    allow(ALLOWED_EXTRA, "go", "exactdecimal", "go exact decimal codec, internal to the module");
    allow(ALLOWED_FILE, "go", "pkg/models/json_marshal.go", "go JSON marshalling helper of the models");
    allow(ALLOWED_FILE, "go", "pkg/models/set.go", "go set type used by the models");
    allow(ALLOWED_FILE, "rust", "src/models/base64_serde.rs", "rust serde helper for base64 fields");
  }

  private static final class Layout {
    final Map<String, Map<String, List<String>>> roles = new TreeMap<>();
    final List<String> unknown = new ArrayList<>();
    final Set<String> core = new TreeSet<>();
  }

  private static final class Holder {
    static final Layout LAYOUT = load();
  }

  private static Layout layout() {
    return Holder.LAYOUT;
  }

  private static Layout load() {
    try {
      return read();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Layout read() throws IOException {
    FileRoleNormaliser.SpecNames spec = FileRoleNormaliser.SpecNames.read(SPEC);
    Layout result = new Layout();
    for (String language : FileRoleNormaliser.languages()) {
      FileRoleNormaliser normaliser = new FileRoleNormaliser(language, spec);
      for (String file : files(language)) {
        Classified classified = normaliser.classify(file);
        if (classified.kind == Kind.SOURCE) {
          result
              .roles
              .computeIfAbsent(classified.role, k -> new TreeMap<>())
              .computeIfAbsent(language, k -> new ArrayList<>())
              .add(file);
        } else if (classified.kind == Kind.UNKNOWN) {
          result.unknown.add(language + " " + file);
        }
      }
    }
    for (Map.Entry<String, Map<String, List<String>>> role : result.roles.entrySet()) {
      if (role.getValue().size() >= CORE_THRESHOLD) {
        result.core.add(role.getKey());
      }
    }
    return result;
  }

  /**
   * The tracked files of one golden folder, plus new files that are not ignored. Build output a
   * spec run leaves in the folder (caches, lock files) is ignored by git and is not a golden.
   */
  private static List<String> files(String language) throws IOException {
    Path root = GOLDENS.resolve(language);
    Process process =
        new ProcessBuilder(
                "git", "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", ".")
            .directory(root.toFile())
            .redirectErrorStream(false)
            .start();
    byte[] output = process.getInputStream().readAllBytes();
    try {
      if (process.waitFor() != 0) {
        throw new IOException("git ls-files failed in " + root);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while listing " + root, e);
    }
    List<String> files = new ArrayList<>();
    for (String file : new String(output, StandardCharsets.UTF_8).split("\u0000", -1)) {
      if (!file.isEmpty() && Files.isRegularFile(root.resolve(file))) {
        files.add(file);
      }
    }
    Collections.sort(files);
    return files;
  }

  private static boolean allowed(
      Map<String, Map<String, String>> table, String language, String key) {
    Map<String, String> forLanguage = table.get(language);
    return forLanguage != null && forLanguage.containsKey(key);
  }

  private static String expected(Layout layout, String language, String role) {
    Map<String, List<String>> have = layout.roles.get(role);
    String sample = "";
    if (have != null) {
      List<String> sampleFiles = have.values().iterator().next();
      sample = sampleFiles.get(0).replaceAll(".*/", "").replaceAll("\\.[^.]*$", "");
    }
    if (role.startsWith("error:")) {
      sample = sample.replaceAll("(Exception|Error|_exception|_error|-exception|-error)$", "");
      sample = sample + (EXCEPTION_SUFFIX.contains(language) ? "Exception" : "Error");
    }
    return FileRoleNormaliser.expectedFileName(language, sample);
  }

  private static String nearest(Layout layout, String role) {
    String best = "none";
    int bestDistance = Integer.MAX_VALUE;
    for (String candidate : layout.core) {
      int distance = distance(role, candidate);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    return best;
  }

  private static int distance(String a, String b) {
    int[] previous = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      int[] current = new int[b.length() + 1];
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
      }
      previous = current;
    }
    return previous[b.length()];
  }

  @Test
  void everyLanguageHasEveryCoreRole() {
    Layout layout = layout();
    List<String> problems = new ArrayList<>();
    for (Map.Entry<String, Map<String, List<String>>> entry : layout.roles.entrySet()) {
      String role = entry.getKey();
      if (!layout.core.contains(role)) {
        continue;
      }
      Set<String> have = entry.getValue().keySet();
      for (String language : FileRoleNormaliser.languages()) {
        if (!have.contains(language) && !allowed(ALLOWED_MISSING, language, role)) {
          problems.add(
              language
                  + " is missing the core role '"
                  + role
                  + "' (languages that have it: "
                  + new TreeSet<>(have)
                  + "); expected a file like "
                  + expected(layout, language, role));
        }
      }
    }
    assertThat(problems).as("missing core roles").isEmpty();
  }

  @Test
  void noLanguageHasAnExtraneousFile() {
    Layout layout = layout();
    List<String> problems = new ArrayList<>();
    for (String entry : layout.unknown) {
      String language = entry.substring(0, entry.indexOf(' '));
      String file = entry.substring(entry.indexOf(' ') + 1);
      if (!allowed(ALLOWED_FILE, language, file)) {
        problems.add(language + " has the extraneous file " + file);
      }
    }
    for (Map.Entry<String, Map<String, List<String>>> role : layout.roles.entrySet()) {
      if (layout.core.contains(role.getKey())) {
        continue;
      }
      for (Map.Entry<String, List<String>> have : role.getValue().entrySet()) {
        if (!allowed(ALLOWED_EXTRA, have.getKey(), role.getKey())) {
          problems.add(
              have.getKey()
                  + " has the extraneous file "
                  + have.getValue()
                  + " (role '"
                  + role.getKey()
                  + "', nearest core role '"
                  + nearest(layout, role.getKey())
                  + "')");
        }
      }
    }
    assertThat(problems).as("extraneous files").isEmpty();
  }

  @Test
  void noRoleIsSpelledTwiceInOneLanguage() {
    Layout layout = layout();
    List<String> problems = new ArrayList<>();
    for (Map.Entry<String, Map<String, List<String>>> role : layout.roles.entrySet()) {
      for (Map.Entry<String, List<String>> have : role.getValue().entrySet()) {
        if (have.getValue().size() > 1 && !allowed(ALLOWED_DUPLICATE, have.getKey(), role.getKey())) {
          problems.add(
              have.getKey() + " spells the role '" + role.getKey() + "' twice: " + have.getValue());
        }
      }
    }
    assertThat(problems).as("duplicated roles").isEmpty();
  }

  @Test
  void everyAllowedExceptionStillApplies() {
    Layout layout = layout();
    List<String> stale = new ArrayList<>();
    for (Map.Entry<String, Map<String, String>> table : ALLOWED_MISSING.entrySet()) {
      for (String role : table.getValue().keySet()) {
        Map<String, List<String>> have = layout.roles.get(role);
        if (!layout.core.contains(role) || (have != null && have.containsKey(table.getKey()))) {
          stale.add(table.getKey() + " no longer needs the missing-role exception " + role);
        }
      }
    }
    for (Map.Entry<String, Map<String, String>> table : ALLOWED_EXTRA.entrySet()) {
      for (String role : table.getValue().keySet()) {
        Map<String, List<String>> have = layout.roles.get(role);
        if (have == null || !have.containsKey(table.getKey()) || layout.core.contains(role)) {
          stale.add(table.getKey() + " no longer needs the extra-role exception " + role);
        }
      }
    }
    for (Map.Entry<String, Map<String, String>> table : ALLOWED_DUPLICATE.entrySet()) {
      for (String role : table.getValue().keySet()) {
        Map<String, List<String>> have = layout.roles.get(role);
        if (have == null || !have.containsKey(table.getKey()) || have.get(table.getKey()).size() < 2) {
          stale.add(table.getKey() + " no longer needs the duplicate-role exception " + role);
        }
      }
    }
    for (Map.Entry<String, Map<String, String>> table : ALLOWED_FILE.entrySet()) {
      for (String file : table.getValue().keySet()) {
        if (!layout.unknown.contains(table.getKey() + " " + file)) {
          stale.add(table.getKey() + " no longer needs the file exception " + file);
        }
      }
    }
    assertThat(stale).as("stale exceptions").isEmpty();
  }

  @Test
  void theCoreRolesAreTheSharedArchitecture() {
    Layout layout = layout();
    assertThat(layout.core)
        .contains(
            "objectserializer",
            "apiclient",
            "baseapi",
            "configuration",
            "valueserializer",
            "headerselector",
            "tracecontextutil",
            "transportoptions",
            "authenticator",
            "oauth2tokenmanager",
            "error:api",
            "error:notfound");
    assertThat(layout.core.size()).isGreaterThanOrEqualTo(50);
  }
}
