package io.github.mridang.codegen.spec.parity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compares the test suites of the languages that ran against the behaviour catalogue, by id.
 * Every test name carries a behaviour id (see {@link BehaviourId}); each language must cover
 * every catalogue behaviour exactly once (a {@code multi} behaviour any number of times), unless
 * the exceptions file excuses it. The comparison covers only the languages present, so a subset
 * run is judged against the same catalogue.
 */
public final class ParityIdComparison {

  private static final int SAMPLE = 20;

  private ParityIdComparison() {}

  /** The problems that fail the spec, empty when every language matches the catalogue. */
  public static List<String> compare(
      Map<String, ParitySummary> present,
      TestCatalogue catalogue,
      TestCatalogueExceptions exceptions) {
    List<String> problems = new ArrayList<>();
    for (ParitySummary summary : present.values()) {
      String language = summary.language();
      for (String error : summary.errors()) {
        problems.add(language + ": " + error);
      }
      if (summary.total() == 0) {
        problems.add(language + ": the reports hold zero tests");
      }
      check(language, summary.names(), catalogue, exceptions, problems);
    }
    for (String language : TestCatalogueExceptions.LANGUAGES) {
      for (String id : exceptions.idsOf(language)) {
        if (!TestCatalogueExceptions.UNNUMBERED.equals(id) && !catalogue.contains(id)) {
          problems.add(
              language + ": exception for " + id + " names an id that is not in the catalogue");
        }
      }
    }
    return problems;
  }

  private static void check(
      String language,
      List<String> names,
      TestCatalogue catalogue,
      TestCatalogueExceptions exceptions,
      List<String> problems) {
    SortedMap<String, List<String>> byId = new TreeMap<>();
    List<String> unnumbered = new ArrayList<>();
    for (String name : names) {
      BehaviourId.extract(name)
          .ifPresentOrElse(
              id -> byId.computeIfAbsent(id, k -> new ArrayList<>()).add(name),
              () -> unnumbered.add(name));
    }

    List<String> missing = new ArrayList<>();
    for (TestCatalogue.Behaviour behaviour : catalogue.all()) {
      boolean has = byId.containsKey(behaviour.id());
      boolean excepted = exceptions.allows(language, behaviour.id());
      if (!has && !excepted) {
        missing.add(behaviour.id() + " - " + behaviour.promise());
      } else if (has && excepted) {
        problems.add(
            language
                + ": stale exception "
                + behaviour.id()
                + " ("
                + exceptions.reason(language, behaviour.id())
                + "): the language covers it, remove the line from"
                + " test-catalogue-exceptions.txt");
      }
    }
    report(problems, language, "missing behaviours (add a test named from the id)", missing);

    SortedSet<String> unknown = new TreeSet<>();
    List<String> duplicates = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : byId.entrySet()) {
      if (!catalogue.contains(entry.getKey())) {
        unknown.add(entry.getKey() + " (" + String.join(", ", entry.getValue()) + ")");
      } else if (entry.getValue().size() > 1 && !catalogue.get(entry.getKey()).multi()) {
        duplicates.add(entry.getKey() + " in " + String.join(", ", entry.getValue()));
      }
    }
    report(problems, language, "unknown ids, not in the catalogue", new ArrayList<>(unknown));
    report(
        problems,
        language,
        "duplicate ids (mark the catalogue line '| multi' if intended)",
        duplicates);

    boolean allowed = exceptions.allows(language, TestCatalogueExceptions.UNNUMBERED);
    if (!unnumbered.isEmpty() && !allowed) {
      report(problems, language, "tests without a behaviour id", unnumbered);
    } else if (unnumbered.isEmpty() && allowed) {
      problems.add(
          language
              + ": stale exception "
              + TestCatalogueExceptions.UNNUMBERED
              + ": every test carries an id, remove the line from test-catalogue-exceptions.txt");
    }
  }

  private static void report(
      List<String> problems, String language, String what, List<String> items) {
    if (items.isEmpty()) {
      return;
    }
    StringBuilder line = new StringBuilder(language + ": " + what + " (" + items.size() + "):");
    for (int i = 0; i < items.size() && i < SAMPLE; i++) {
      line.append("\n  ").append(items.get(i));
    }
    if (items.size() > SAMPLE) {
      line.append("\n  ... and ").append(items.size() - SAMPLE).append(" more");
    }
    problems.add(line.toString());
  }
}
