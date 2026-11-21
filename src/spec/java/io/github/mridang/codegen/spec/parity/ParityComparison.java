package io.github.mridang.codegen.spec.parity;

import io.github.mridang.codegen.spec.parity.ParityAllowList.Kind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compares the test suites of the languages that ran. The unit under test of a test is its
 * group (for example {@code objectserializer}) and its key is {@code group::test}.
 *
 * <p>A group or test is <em>common</em> when at least two thirds of the present languages have
 * it (and never fewer than two). A language that lacks a common one is missing it; a language
 * whose group no other language has carries an extra group, which usually means a renamed class
 * or a test file the others never got. Tests only one language has are normal (a language adds
 * its own cases) and are reported, not failed.
 *
 * <p>Every gap must be listed with a reason in the allow file. Group gaps are checked for every
 * run. Test gaps are only judged against the whole family, so they are checked only when all
 * twelve languages are present, and so are allow entries that no longer match a gap.
 */
public final class ParityComparison {

  /** The number of languages in the family. */
  public static final int FAMILY_SIZE = 12;

  private static final int SAMPLE = 10;

  /** The gaps of one language, before the allow list is applied. */
  public static final class Gaps {
    final SortedSet<String> missingGroups = new TreeSet<>();
    final SortedSet<String> extraGroups = new TreeSet<>();
    final SortedSet<String> missingTests = new TreeSet<>();
    final SortedSet<String> extraTests = new TreeSet<>();

    public SortedSet<String> missingGroups() {
      return missingGroups;
    }

    public SortedSet<String> extraGroups() {
      return extraGroups;
    }

    public SortedSet<String> missingTests() {
      return missingTests;
    }

    public SortedSet<String> extraTests() {
      return extraTests;
    }
  }

  /** The outcome: the gaps per language and the problems that fail the spec. */
  public static final class Result {
    private final Map<String, Gaps> gaps;
    private final List<String> problems;
    private final SortedSet<String> partialGroups;

    Result(Map<String, Gaps> gaps, List<String> problems, SortedSet<String> partialGroups) {
      this.gaps = gaps;
      this.problems = problems;
      this.partialGroups = partialGroups;
    }

    /** The groups that more than one but fewer than a quorum of the languages have. */
    public SortedSet<String> partialGroups() {
      return partialGroups;
    }

    public Map<String, Gaps> gaps() {
      return gaps;
    }

    /** The gaps of one language that ran. */
    public Gaps gapsOf(String language) {
      Gaps found = gaps.get(language);
      if (found == null) {
        throw new IllegalArgumentException("No reports for " + language);
      }
      return found;
    }

    public List<String> problems() {
      return problems;
    }
  }

  private ParityComparison() {}

  /** The number of languages that must have a group or test for it to count as common. */
  public static int quorum(int languages) {
    return Math.max(2, (2 * languages + 2) / 3);
  }

  public static Result compare(Map<String, ParitySummary> present, ParityAllowList allow) {
    List<String> problems = new ArrayList<>();
    boolean complete = present.size() >= FAMILY_SIZE;
    int quorum = quorum(present.size());

    for (ParitySummary summary : present.values()) {
      for (String error : summary.errors()) {
        problems.add(summary.language() + ": " + error);
      }
      if (summary.total() == 0) {
        problems.add(summary.language() + ": the reports hold zero tests");
      }
    }

    Map<String, SortedSet<String>> groups = new TreeMap<>();
    Map<String, SortedSet<String>> tests = new TreeMap<>();
    for (ParitySummary summary : present.values()) {
      groups.put(summary.language(), summary.groups());
      tests.put(summary.language(), summary.keys());
    }
    Map<String, Integer> groupCount = count(groups.values());
    SortedSet<String> partialGroups = new TreeSet<>();
    for (Map.Entry<String, Integer> group : groupCount.entrySet()) {
      if (group.getValue() >= 2 && group.getValue() < quorum) {
        partialGroups.add(group.getKey());
      }
    }
    Map<String, Integer> testCount = count(tests.values());

    Map<String, Gaps> result = new TreeMap<>();
    for (String language : present.keySet()) {
      Gaps gaps = new Gaps();
      for (Map.Entry<String, Integer> group : groupCount.entrySet()) {
        boolean has = own(groups, language).contains(group.getKey());
        if (!has && group.getValue() >= quorum) {
          gaps.missingGroups.add(group.getKey());
        } else if (has && group.getValue() == 1) {
          gaps.extraGroups.add(group.getKey());
        }
      }
      for (Map.Entry<String, Integer> test : testCount.entrySet()) {
        boolean has = own(tests, language).contains(test.getKey());
        String group = test.getKey().substring(0, test.getKey().indexOf("::"));
        if (!has && test.getValue() >= quorum && !gaps.missingGroups.contains(group)) {
          gaps.missingTests.add(test.getKey());
        } else if (has && test.getValue() == 1) {
          gaps.extraTests.add(test.getKey());
        }
      }
      result.put(language, gaps);
    }

    for (Map.Entry<String, Gaps> entry : result.entrySet()) {
      String language = entry.getKey();
      Gaps gaps = entry.getValue();
      List<String> missingGroups =
          unallowedMissingGroups(language, gaps, groups, allow, complete);
      List<String> extraGroups = unallowedExtraGroups(language, gaps, groups, allow, complete);
      report(problems, language, "missing groups", missingGroups);
      report(problems, language, "extra groups", extraGroups);
      if (complete) {
        report(problems, language, "missing tests", unallowed(language, Kind.MISSING_TESTS, gaps.missingTests, allow));
      }
    }
    if (complete) {
      partialGroupProblems(problems, allow, partialGroups);
      staleEntries(problems, allow, result);
    }
    return new Result(result, problems, partialGroups);
  }

  /**
   * A group gap between two languages is one disagreement, and which side is blamed depends on
   * how many languages ran. So that a run of a few languages is judged by the same file as the
   * whole family, the gap is also covered when the file lists it against the other side.
   */
  private static List<String> unallowedMissingGroups(
      String language,
      Gaps gaps,
      Map<String, SortedSet<String>> groups,
      ParityAllowList allow,
      boolean complete) {
    List<String> unallowed = new ArrayList<>();
    for (String group : gaps.missingGroups) {
      if (allow.allows(language, Kind.MISSING_GROUPS, group)) {
        continue;
      }
      if (!complete && coveredElsewhere(group, groups, allow)) {
        continue;
      }
      unallowed.add(group);
    }
    return unallowed;
  }

  private static List<String> unallowedExtraGroups(
      String language,
      Gaps gaps,
      Map<String, SortedSet<String>> groups,
      ParityAllowList allow,
      boolean complete) {
    List<String> unallowed = new ArrayList<>();
    for (String group : gaps.extraGroups) {
      if (allow.allows(language, Kind.EXTRA_GROUPS, group)) {
        continue;
      }
      if (!complete && coveredElsewhere(group, groups, allow)) {
        continue;
      }
      unallowed.add(group);
    }
    return unallowed;
  }

  /**
   * Whether the allow file covers a group disagreement of a run of fewer than all languages in
   * any way: the group is listed as partial, a language that has it lists it as extra, or a
   * language that lacks it lists it as missing.
   */
  private static boolean coveredElsewhere(
      String group, Map<String, SortedSet<String>> groups, ParityAllowList allow) {
    if (allow.allowsPartialGroup(group)) {
      return true;
    }
    for (Map.Entry<String, SortedSet<String>> entry : groups.entrySet()) {
      Kind kind = entry.getValue().contains(group) ? Kind.EXTRA_GROUPS : Kind.MISSING_GROUPS;
      if (allow.allows(entry.getKey(), kind, group)) {
        return true;
      }
    }
    return false;
  }

  private static void partialGroupProblems(
      List<String> problems, ParityAllowList allow, SortedSet<String> partialGroups) {
    List<String> unlisted = new ArrayList<>();
    for (String group : partialGroups) {
      if (!allow.allowsPartialGroup(group)) {
        unlisted.add(group);
      }
    }
    if (!unlisted.isEmpty()) {
      problems.add(
          "partial groups ("
              + unlisted.size()
              + ", only some languages have them, list them in partialGroups): "
              + String.join(", ", unlisted));
    }
    for (ParityAllowList.Entry entry : allow.partialGroups()) {
      for (String pattern : entry.keys()) {
        boolean used = false;
        for (String group : partialGroups) {
          if (ParityAllowList.matches(pattern, group)) {
            used = true;
            break;
          }
        }
        if (!used) {
          problems.add(
              "the allow entry partialGroups '"
                  + pattern
                  + "' is not a partial group any more, remove it");
        }
      }
    }
  }

  private static List<String> unallowed(
      String language, Kind kind, Collection<String> keys, ParityAllowList allow) {
    List<String> unallowed = new ArrayList<>();
    for (String key : keys) {
      if (!allow.allows(language, kind, key)) {
        unallowed.add(key);
      }
    }
    return unallowed;
  }

  private static void report(
      List<String> problems, String language, String what, List<String> keys) {
    if (!keys.isEmpty()) {
      problems.add(language + ": " + what + " (" + keys.size() + "): " + String.join(", ", keys));
    }
  }

  private static void staleEntries(
      List<String> problems, ParityAllowList allow, Map<String, Gaps> result) {
    for (String language : allow.languages()) {
      Gaps gaps = result.get(language);
      if (gaps == null) {
        problems.add(language + ": the allow file names a language that has no reports");
        continue;
      }
      stale(problems, allow, language, Kind.MISSING_GROUPS, gaps.missingGroups);
      stale(problems, allow, language, Kind.EXTRA_GROUPS, gaps.extraGroups);
    }
  }

  private static void stale(
      List<String> problems,
      ParityAllowList allow,
      String language,
      Kind kind,
      Collection<String> gaps) {
    for (ParityAllowList.Entry entry : allow.entries(language, kind)) {
      for (String pattern : entry.keys()) {
        boolean used = false;
        for (String gap : gaps) {
          if (ParityAllowList.matches(pattern, gap)) {
            used = true;
            break;
          }
        }
        if (!used) {
          problems.add(
              language
                  + ": the allow entry "
                  + kind.jsonName()
                  + " '"
                  + pattern
                  + "' covers no gap any more, remove it");
        }
      }
    }
  }

  /** A line per language for the log: the gap counts and a sample of the extra tests. */
  public static List<String> describe(Map<String, Gaps> gaps) {
    List<String> lines = new ArrayList<>();
    for (Map.Entry<String, Gaps> entry : gaps.entrySet()) {
      Gaps g = entry.getValue();
      List<String> sample = new ArrayList<>(g.extraTests.size() > SAMPLE ? new ArrayList<>(g.extraTests).subList(0, SAMPLE) : g.extraTests);
      lines.add(
          entry.getKey()
              + ": "
              + g.missingGroups.size()
              + " missing groups, "
              + g.extraGroups.size()
              + " extra groups, "
              + g.missingTests.size()
              + " missing tests, "
              + g.extraTests.size()
              + " tests no other language has, for example "
              + String.join(", ", sample));
    }
    return lines;
  }

  private static SortedSet<String> own(Map<String, SortedSet<String>> sets, String language) {
    SortedSet<String> set = sets.get(language);
    return set == null ? new TreeSet<>() : set;
  }

  private static Map<String, Integer> count(Collection<SortedSet<String>> sets) {
    Map<String, Integer> count = new HashMap<>();
    for (SortedSet<String> set : sets) {
      for (String key : set) {
        count.merge(key, 1, Integer::sum);
      }
    }
    return new TreeMap<>(count);
  }
}
