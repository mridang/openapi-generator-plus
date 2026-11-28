package io.github.mridang.codegen.spec.parity;

import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps one test of one language to a canonical, comparable key of the form
 * {@code subject::test}. The twelve SDKs name their tests in twelve styles (Java
 * {@code testFooBar}, Python {@code test_foo_bar}, Go {@code TestFoo_Bar}, Rust
 * {@code test_foo_bar}, Swift {@code testFooBar()}, Elixir {@code "test foo bar"}, C#
 * {@code FooBar}, Ruby {@code test_0001_foo}, PHP, Node and Dart sentences), so every name is
 * reduced to its lower-case alphanumeric words joined together, without the language's test
 * prefix, the generated-case number, a parameter suffix or the subject it repeats.
 *
 * <p>The subject is the unit under test, taken from the class, module, suite or function-name
 * prefix of the language, so {@code ObjectSerializerTest}, {@code test_object_serializer} and
 * {@code object_serializer_test} all name the subject {@code objectserializer}.
 */
public final class TestNameNormaliser {

  private static final Pattern PARAMETERS = Pattern.compile("\\([^()]*\\)$");
  private static final Pattern BRACKETS = Pattern.compile("\\[[^\\]]*\\]$");
  private static final Pattern DATA_SET = Pattern.compile(" with data set .*$");
  private static final Pattern RUBY_CASE = Pattern.compile("^test_\\d+_");
  private static final Pattern GO_FUNCTION = Pattern.compile("^Test([^_/]+)");
  private static final Pattern CAMEL_ACRONYM = Pattern.compile("([A-Z]+)([A-Z][a-z])");
  private static final Pattern CAMEL_LOWER_UPPER = Pattern.compile("([a-z0-9])([A-Z])");
  private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]+");

  private TestNameNormaliser() {}

  /** One canonical test: the unit under test and the normalised test name. */
  public static final class Key {
    private final String subject;
    private final String test;

    Key(String subject, String test) {
      this.subject = subject;
      this.test = test;
    }

    public String subject() {
      return subject;
    }

    public String test() {
      return test;
    }

    /** The comparable form, {@code subject::test}. */
    public String canonical() {
      return subject + "::" + test;
    }

    @Override
    public String toString() {
      return canonical();
    }
  }

  /**
   * Normalises one test case of a JUnit report.
   *
   * @param language the short language id, such as {@code java} or {@code csharp}
   * @param classname the {@code classname} attribute of the test case
   * @param suiteName the {@code name} attribute of the enclosing test suite
   * @param testName the {@code name} attribute of the test case
   */
  public static Key normalise(
      String language, String classname, String suiteName, String testName) {
    String subject = subject(language, classname, suiteName, testName);
    return new Key(subject, testPart(language, testName, subject));
  }

  /** Splits an identifier or sentence into its lower-case alphanumeric words. */
  static List<String> words(String text) {
    String spaced = CAMEL_ACRONYM.matcher(text).replaceAll("$1 $2");
    spaced = CAMEL_LOWER_UPPER.matcher(spaced).replaceAll("$1 $2");
    List<String> words = new ArrayList<>();
    for (String word : Splitter.on(NON_ALNUM).omitEmptyStrings().split(spaced)) {
      words.add(word.toLowerCase(Locale.ROOT));
    }
    return words;
  }

  private static String subject(
      String language, String classname, String suiteName, String testName) {
    switch (language) {
      case "java":
      case "kotlin":
        return className(afterLast(beforeFirst(classname, '$'), '.'));
      case "csharp":
      case "php":
      case "swift":
      case "elixir":
      case "dart":
        return className(afterLast(classname, '.'));
      case "python":
        for (String part : Splitter.on('.').split(classname)) {
          if (part.startsWith("test_")) {
            return className(part.substring("test_".length()));
          }
        }
        return className(classname);
      case "ruby":
        return rubySubject(classname);
      case "node":
        return className(suiteName);
      case "go":
        Matcher function = GO_FUNCTION.matcher(testName);
        return className(function.find() ? function.group(1) : testName);
      case "rust":
        return className(
            classname.contains("::") ? afterLast(classname, ':') : rustModule(testName));
      default:
        throw new IllegalArgumentException("Unknown language: " + language);
    }
  }

  /** The module of an in-crate test: the path segment before {@code tests}, or the first one. */
  private static String rustModule(String testName) {
    List<String> path = Splitter.on("::").splitToList(testName);
    int tests = path.indexOf("tests");
    return path.get(tests > 0 ? tests - 1 : 0);
  }

  private static String rubySubject(String classname) {
    List<String> parts = Splitter.on("::").splitToList(classname);
    int start = parts.size() > 2 && "Petstore".equals(parts.get(0)) ? 2 : 0;
    while (start < parts.size() - 1 && isNamespace(parts.get(start))) {
      start++;
    }
    return className(parts.get(start));
  }

  private static boolean isNamespace(String part) {
    return "Api".equals(part) || "Auth".equals(part) || "Errors".equals(part) || "OAuth".equals(part);
  }

  /**
   * Reduces a class or module name to a subject: no trailing {@code Test}, {@code Tests} or
   * {@code Spec}, nothing after an inner {@code Test} (C# splits one subject over several
   * {@code ObjectSerializerTestFoo} classes), and {@code Exception} read as {@code Error}
   * because the Java and C# SDKs name the type {@code ApiException} where the others name it
   * {@code ApiError}.
   */
  private static String className(String name) {
    List<String> words = words(name);
    for (int i = 1; i < words.size(); i++) {
      if (isTestWord(words.get(i))) {
        words = new ArrayList<>(words.subList(0, i));
        break;
      }
    }
    while (!words.isEmpty() && (isTestWord(words.get(words.size() - 1)) || "spec".equals(words.get(words.size() - 1)))) {
      words.remove(words.size() - 1);
    }
    return join(words);
  }

  private static boolean isTestWord(String word) {
    return "test".equals(word) || "tests".equals(word);
  }

  private static String testPart(String language, String testName, String subject) {
    String name = DATA_SET.matcher(testName).replaceAll("");
    switch (language) {
      case "java":
      case "kotlin":
        name = name.replace("[jvm]", "");
        name = PARAMETERS.matcher(name).replaceAll("");
        break;
      case "swift":
        name = PARAMETERS.matcher(name).replaceAll("");
        break;
      case "csharp":
        name = afterLast(beforeFirst(name, '('), '.');
        break;
      case "rust":
        name = afterLast(name, ':');
        break;
      case "go":
        if (name.indexOf('_') >= 0) {
          name = name.substring(name.indexOf('_') + 1);
        }
        break;
      case "ruby":
        name = RUBY_CASE.matcher(name).replaceAll("");
        break;
      default:
        break;
    }
    name = BRACKETS.matcher(name).replaceAll("");

    List<String> words = new ArrayList<>(words(name));
    if (!words.isEmpty() && "test".equals(words.get(0))) {
      words.remove(0);
    }
    return join(stripSubject(words, subject));
  }

  /** Drops the words at the front of a test name that only repeat its subject. */
  private static List<String> stripSubject(List<String> words, String subject) {
    StringBuilder prefix = new StringBuilder();
    for (int i = 0; i < words.size(); i++) {
      prefix.append(words.get(i));
      String candidate = prefix.toString().replace("exception", "error");
      if (candidate.equals(subject)) {
        return words.subList(i + 1, words.size());
      }
      if (!subject.startsWith(candidate)) {
        break;
      }
    }
    return words;
  }

  private static String join(List<String> words) {
    return String.join("", words).replace("exception", "error");
  }

  private static String afterLast(String text, char separator) {
    return text.substring(text.lastIndexOf(separator) + 1);
  }

  private static String beforeFirst(String text, char separator) {
    int index = text.indexOf(separator);
    return index < 0 ? text : text.substring(0, index);
  }
}
