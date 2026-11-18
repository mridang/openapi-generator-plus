package io.github.mridang.codegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns a path inside a committed golden SDK into a canonical role key, so the file layouts of the
 * twelve languages can be compared with each other. {@code object_serializer.py},
 * {@code ObjectSerializer.java} and {@code object-serializer.ts} all become {@code objectserializer}.
 */
final class FileRoleNormaliser {

  /** What a file in a golden SDK is for. */
  enum Kind {
    /** A source file named by a role; whether it is core is decided by how many languages have it. */
    SOURCE,
    /** A model, API or options file named after something in the fixture spec. */
    PER_SPEC,
    /** A test, fixture or spec file; covered by the test parity check. */
    TEST,
    /** A manifest, build, lint or documentation file listed per language with a reason. */
    MANIFEST,
    /** A package entry point (module index) that every language spells differently. */
    PACKAGE_INDEX,
    /** A type declaration companion file such as Ruby's RBS signatures. */
    TYPE_DECLARATION,
    /** A file that fits no other kind. */
    UNKNOWN
  }

  /** The result of classifying one path. */
  static final class Classified {
    final Kind kind;
    final String role;
    final String reason;

    Classified(Kind kind, String role, String reason) {
      this.kind = kind;
      this.role = role;
      this.reason = reason;
    }
  }

  /** The names in the fixture spec that per-spec files are matched against. */
  static final class SpecNames {
    final Set<String> schemas = new HashSet<>();
    final Set<String> tags = new HashSet<>();
    final Set<String> operations = new HashSet<>();

    static SpecNames read(File yaml) throws IOException {
      JsonNode root = new YAMLMapper().readTree(yaml);
      SpecNames names = new SpecNames();
      Iterator<String> schemaNames = root.path("components").path("schemas").fieldNames();
      while (schemaNames.hasNext()) {
        names.schemas.add(roleName(schemaNames.next()));
      }
      for (JsonNode tag : root.path("tags")) {
        names.tags.add(roleName(tag.path("name").asText()));
      }
      for (JsonNode path : root.path("paths")) {
        for (JsonNode operation : path) {
          if (operation.isObject() && operation.has("operationId")) {
            String operationId = roleName(operation.get("operationId").asText());
            names.operations.add(operationId);
            // inline request and response bodies become models named after the operation
            names.schemas.add(operationId + "request");
            names.schemas.add(operationId + "response");
          }
          for (JsonNode tag : operation.path("tags")) {
            names.tags.add(roleName(tag.asText()));
          }
        }
      }
      return names;
    }
  }

  /** Where each language keeps its library source, longest prefix first. */
  private static final Map<String, List<String>> SOURCE_ROOTS = new LinkedHashMap<>();

  /** Segments that mark a test, fixture or spec folder. */
  private static final Set<String> TEST_FOLDERS =
      new HashSet<>(Arrays.asList("test", "tests", "spec", "testdata", "fixtures"));

  /** Names that are the package entry point of a folder in some language. */
  private static final Set<String> PACKAGE_INDEX_NAMES =
      new HashSet<>(Arrays.asList("init", "index", "mod", "lib", "petstoreclient"));

  /** Per language: a file name that is the same role as another name in the other languages. */
  private static final Map<String, Map<String, String>> ALIASES = new LinkedHashMap<>();

  /** Per language: a path relative to the language folder, and why the file is allowed. */
  private static final Map<String, Map<String, String>> MANIFESTS = new LinkedHashMap<>();

  static {
    SOURCE_ROOTS.put("csharp", Arrays.asList("src/PetstoreClient/"));
    SOURCE_ROOTS.put("dart", Arrays.asList("lib/src/", "lib/"));
    SOURCE_ROOTS.put("elixir", Arrays.asList("lib/petstore_client/", "lib/"));
    SOURCE_ROOTS.put("go", Arrays.asList("pkg/"));
    SOURCE_ROOTS.put("java", Arrays.asList("src/main/java/com/example/petstore/"));
    SOURCE_ROOTS.put(
        "kotlin",
        Arrays.asList(
            "src/main/kotlin/com/example/petstore/", "src/jvmMain/kotlin/com/example/petstore/"));
    SOURCE_ROOTS.put("node", Arrays.asList("src/"));
    SOURCE_ROOTS.put("php", Arrays.asList("lib/"));
    SOURCE_ROOTS.put("python", Arrays.asList("petstore_client/"));
    SOURCE_ROOTS.put("ruby", Arrays.asList("lib/petstore/client/", "lib/"));
    SOURCE_ROOTS.put("rust", Arrays.asList("src/"));
    SOURCE_ROOTS.put("swift", Arrays.asList("Sources/PetstoreClient/"));

    Map<String, String> rubyAliases = new LinkedHashMap<>();
    rubyAliases.put("petstore", "client");
    ALIASES.put("ruby", rubyAliases);
    Map<String, String> csharpAliases = new LinkedHashMap<>();
    csharpAliases.put("notimplementedstatus", "notimplemented");
    csharpAliases.put("notimplementedstatusexception", "notimplementedexception");
    ALIASES.put("csharp", csharpAliases);

    Map<String, String> shared = new LinkedHashMap<>();
    shared.put(".editorconfig", "editor settings shared by every language");
    shared.put(".gitignore", "ignore rules of the generated project");
    shared.put(".openapi-generator/DEV-DEPENDENCIES", "marker the generator writes next to the SDK");
    shared.put("Makefile", "the one entry point for build, lint and test commands");
    shared.put("README.md", "usage documentation of the SDK");
    shared.put("SKILLS.md", "agent skill file shipped with every SDK");
    for (String language : SOURCE_ROOTS.keySet()) {
      MANIFESTS.put(language, new LinkedHashMap<>(shared));
    }
    manifest("csharp", ".config/dotnet-tools.json", "pins the dotnet tools (formatter) of the project");
    manifest("csharp", "PetstoreClient.Test.csproj", "the dotnet test project file");
    manifest("csharp", "docfx.json", "docfx API documentation build configuration");
    manifest("csharp", "index.md", "docfx documentation landing page");
    manifest("csharp", "toc.yml", "docfx documentation table of contents");
    manifest("csharp", "xunit.runner.json", "xunit runner configuration");
    manifest("csharp", "src/PetstoreClient/PetstoreClient.csproj", "the dotnet library project file");
    manifest("csharp", "src/PetstoreClient/ILLink.Descriptors.xml", "trimming descriptor so AOT keeps models");
    manifest("dart", "analysis_options.yaml", "dart analyzer and lint configuration");
    manifest("dart", "pubspec.yaml", "dart package manifest");
    manifest("elixir", ".credo.exs", "credo lint configuration");
    manifest("elixir", ".formatter.exs", "mix format configuration");
    manifest("elixir", "coveralls.json", "coverage tool configuration");
    manifest("elixir", "mix.exs", "mix project manifest");
    manifest("go", ".golangci.yml", "golangci-lint configuration");
    manifest("go", "go.mod", "go module manifest");
    manifest("go", "go.sum", "go module checksums");
    manifest("java", "pom.xml", "maven project manifest");
    manifest("java", "checkstyle-suppressions.xml", "checkstyle suppressions for generated code");
    manifest(
        "java",
        "src/main/resources/META-INF/native-image/com/example/petstore/reflect-config.json",
        "GraalVM native-image reflection configuration");
    manifest("kotlin", "build.gradle.kts", "gradle build script");
    manifest("kotlin", "settings.gradle.kts", "gradle settings");
    manifest("kotlin", "gradle.properties", "gradle properties");
    manifest("kotlin", "detekt.yml", "detekt lint configuration");
    manifest("node", ".prettierignore", "prettier ignore rules");
    manifest("node", ".prettierrc.json", "prettier configuration");
    manifest("node", "eslint.config.js", "eslint configuration");
    manifest("node", "jest.config.mjs", "jest configuration");
    manifest("node", "package.json", "npm package manifest");
    manifest("node", "tsconfig.json", "typescript compiler configuration");
    manifest("node", "tsconfig.jest.json", "typescript configuration for the tests");
    manifest("node", "typedoc.json", "typedoc API documentation configuration");
    manifest("php", ".php-cs-fixer.dist.php", "php-cs-fixer configuration");
    manifest("php", "composer.json", "composer package manifest");
    manifest("php", "phpcs.xml", "phpcs lint configuration");
    manifest("php", "phpdoc.dist.xml", "phpdoc API documentation configuration");
    manifest("php", "phpstan.neon", "phpstan configuration");
    manifest("php", "phpunit.xml", "phpunit configuration");
    manifest("php", "rector.php", "rector configuration");
    manifest("python", "pyproject.toml", "python project manifest");
    manifest("python", "conftest.py", "pytest fixtures shared by the tests");
    manifest("python", "petstore_client/py.typed", "PEP 561 marker that the package ships types");
    manifest("ruby", ".rubocop.yml", "rubocop configuration");
    manifest("ruby", ".yardopts", "yard API documentation options");
    manifest("ruby", "Gemfile", "bundler dependency manifest");
    manifest("ruby", "Rakefile", "rake tasks");
    manifest("ruby", "Steepfile", "steep type checker configuration");
    manifest("ruby", "petstore_client.gemspec", "gem specification");
    manifest("rust", ".config/nextest/default.toml", "nextest runner configuration");
    manifest("rust", "Cargo.toml", "cargo package manifest");
    manifest("rust", "clippy.toml", "clippy lint configuration");
    manifest("rust", "deny.toml", "cargo-deny licence and advisory configuration");
    manifest("rust", "rustfmt.toml", "rustfmt configuration");
    manifest("swift", ".swift-format", "swift-format configuration");
    manifest("swift", ".swiftlint.yml", "swiftlint configuration");
    manifest("swift", "Package.swift", "swift package manifest");
  }

  private static void manifest(String language, String path, String reason) {
    Map<String, String> files = MANIFESTS.get(language);
    if (files == null) {
      throw new IllegalArgumentException("unknown language " + language);
    }
    files.put(path, reason);
  }

  private final String language;
  private final SpecNames spec;
  private final List<String> roots;
  private final Map<String, String> manifests;

  FileRoleNormaliser(String language, SpecNames spec) {
    List<String> languageRoots = SOURCE_ROOTS.get(language);
    Map<String, String> languageManifests = MANIFESTS.get(language);
    if (languageRoots == null || languageManifests == null) {
      throw new IllegalArgumentException("unknown language " + language);
    }
    this.language = language;
    this.spec = spec;
    this.roots = languageRoots;
    this.manifests = languageManifests;
  }

  /** The languages this normaliser knows. */
  static List<String> languages() {
    return Collections.unmodifiableList(new ArrayList<>(SOURCE_ROOTS.keySet()));
  }

  /** Lower-cases a name and drops every separator, so snake, kebab, camel and Pascal agree. */
  static String roleName(String name) {
    return name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
  }

  /** Classifies a path relative to the language folder. */
  Classified classify(String path) {
    String[] segments = path.split("/", -1);
    String file = segments[segments.length - 1];

    String manifestReason = manifests.get(path);
    if (manifestReason != null) {
      return new Classified(Kind.MANIFEST, roleName(file), manifestReason);
    }
    if (isTest(segments)) {
      return new Classified(Kind.TEST, baseName(file), "under a test folder or named as a test");
    }
    if (path.endsWith(".rbs") || path.startsWith("sig/")) {
      return new Classified(Kind.TYPE_DECLARATION, baseName(file), "type declaration companion");
    }
    String relative = null;
    for (String root : roots) {
      if (path.startsWith(root)) {
        relative = path.substring(root.length());
        break;
      }
    }
    if (relative == null) {
      return new Classified(Kind.UNKNOWN, baseName(file), "outside the source root and not a manifest");
    }
    String[] parts = relative.split("/", -1);
    String name = baseName(parts[parts.length - 1]);
    String alias = ALIASES.getOrDefault(language, Collections.emptyMap()).get(name);
    if (alias != null) {
      name = alias;
    }
    String zone = "";
    for (int i = 0; i < parts.length - 1; i++) {
      String folder = parts[i].toLowerCase(Locale.ROOT);
      if (folder.equals("models") || folder.equals("errors") || folder.equals("options")) {
        zone = folder;
      }
    }
    if (PACKAGE_INDEX_NAMES.contains(name)) {
      return new Classified(Kind.PACKAGE_INDEX, name, "package entry point");
    }
    if (name.endsWith("options") && spec.operations.contains(stripSuffix(name, "options"))) {
      return new Classified(Kind.PER_SPEC, "options:" + name, "options of one operation");
    }
    if (isServerOfOperation(name)) {
      return new Classified(Kind.PER_SPEC, "server:" + name, "server choice of one operation");
    }
    if (name.endsWith("api") && spec.tags.contains(stripSuffix(name, "api"))) {
      return new Classified(Kind.PER_SPEC, "api:" + name, "API of one tag");
    }
    if (zone.equals("models")) {
      String model = spec.schemas.contains(name) ? name : stripPrefix(name, "model");
      if (spec.schemas.contains(model)) {
        return new Classified(Kind.PER_SPEC, "model:" + model, "model of one schema");
      }
      if (name.endsWith("enum")) {
        for (String schema : spec.schemas) {
          if (name.startsWith(schema)) {
            return new Classified(Kind.PER_SPEC, "enum:" + name, "inline enum of one schema");
          }
        }
      }
      return new Classified(Kind.UNKNOWN, "model:" + name, "model that is not a schema of the spec");
    }
    if (zone.equals("errors")) {
      String error = name;
      if (error.endsWith("exception") && error.length() > "exception".length()) {
        error = stripSuffix(error, "exception");
      }
      if (error.endsWith("error") && error.length() > "error".length()) {
        error = stripSuffix(error, "error");
      }
      return new Classified(Kind.SOURCE, "error:" + error, "error type");
    }
    return new Classified(Kind.SOURCE, name, "source file");
  }

  /** Whether a file is a server choice of one operation; PHP emits one per operation. */
  private boolean isServerOfOperation(String name) {
    for (String operation : spec.operations) {
      if (name.startsWith(operation + "server")) {
        return true;
      }
    }
    return false;
  }

  private boolean isTest(String[] segments) {
    for (int i = 0; i < segments.length - 1; i++) {
      if (TEST_FOLDERS.contains(segments[i].toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    String file = segments[segments.length - 1];
    return file.endsWith("_test.go");
  }

  /** The role name of a file name: no extension, no separators, no case, no C# interface prefix. */
  private String baseName(String file) {
    String stem = file;
    int dot = file.lastIndexOf('.');
    if (dot > 0) {
      stem = file.substring(0, dot);
    }
    if (language.equals("csharp") && stem.matches("I[A-Z][a-z].*")) {
      stem = stem.substring(1);
    }
    return roleName(stem);
  }

  /** The word list of a file stem, whatever its case style: ObjectSerializer gives object, serializer. */
  static List<String> words(String stem) {
    List<String> words = new ArrayList<>();
    for (String chunk : stem.split("[^A-Za-z0-9]+", -1)) {
      for (String word : chunk.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", -1)) {
        if (!word.isEmpty()) {
          words.add(word.toLowerCase(Locale.ROOT));
        }
      }
    }
    return words;
  }

  /** The file name a language would use for the words of a sample file, in its own style. */
  static String expectedFileName(String language, String sampleStem) {
    List<String> words = words(sampleStem);
    String style = STYLES.get(language);
    if (style == null) {
      throw new IllegalArgumentException("unknown language " + language);
    }
    StringBuilder name = new StringBuilder();
    String separator = style.equals("kebab") ? "-" : style.equals("snake") ? "_" : "";
    for (int i = 0; i < words.size(); i++) {
      String word = words.get(i);
      if (i > 0) {
        name.append(separator);
      }
      boolean capital = style.equals("pascal");
      name.append(capital ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word);
    }
    return name.toString() + EXTENSIONS.get(language);
  }

  private static final Map<String, String> STYLES = new LinkedHashMap<>();
  private static final Map<String, String> EXTENSIONS = new LinkedHashMap<>();

  static {
    String[][] table = {
      {"csharp", "pascal", ".cs"}, {"dart", "snake", ".dart"}, {"elixir", "snake", ".ex"},
      {"go", "snake", ".go"}, {"java", "pascal", ".java"}, {"kotlin", "pascal", ".kt"},
      {"node", "kebab", ".ts"}, {"php", "pascal", ".php"}, {"python", "snake", ".py"},
      {"ruby", "snake", ".rb"}, {"rust", "snake", ".rs"}, {"swift", "pascal", ".swift"}
    };
    for (String[] row : table) {
      STYLES.put(row[0], row[1]);
      EXTENSIONS.put(row[0], row[2]);
    }
  }

  private static String stripSuffix(String name, String suffix) {
    return name.substring(0, name.length() - suffix.length());
  }

  private static String stripPrefix(String name, String prefix) {
    return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
  }
}
