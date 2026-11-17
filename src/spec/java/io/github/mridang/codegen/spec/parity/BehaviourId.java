package io.github.mridang.codegen.spec.parity;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the behaviour id out of a raw test name. A behaviour id is an area (lower-case letters
 * only) followed by exactly three digits, for example {@code transport014}. A test carries it as
 * the word {@code bhv<area><nnn>} inside its normal name, in the casing of its language
 * ({@code bhvTransport014RetriesOn503}, {@code test_bhv_transport_014_retries_on_503},
 * {@code TestBhvTransport014_RetriesOn503}, {@code 'bhv transport 014 retries on 503'}).
 *
 * <p>The name is lower-cased and every character that is not a letter or digit is removed, then
 * the id is the first match of {@code bhv([a-z]+)(\d{3})}. The area holds letters only, so it can
 * never swallow digits and an area such as {@code authoauth} cannot collide with {@code oauth2}.
 * The three digits must not be followed by a fourth: a name whose description starts with a digit
 * right after the id (for example {@code bhv transport 014 503 ...}) is read as having no id,
 * which is reported, rather than guessed.
 */
public final class BehaviourId {

  /** Matches {@code bhv<area><nnn>} in a name that was already lower-cased and stripped. */
  public static final Pattern PATTERN = Pattern.compile("bhv([a-z]+)(\\d{3})(?!\\d)");

  /** Matches a catalogue id: lower-case letters, then exactly three digits. */
  public static final Pattern CATALOGUE_ID = Pattern.compile("[a-z]+\\d{3}");

  private BehaviourId() {}

  /** The lower-cased name with every separator removed. */
  static String squash(String rawName) {
    return rawName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }

  /** The id ({@code transport014}) of the raw test name, or empty when it carries none. */
  public static Optional<String> extract(String rawName) {
    Matcher matcher = PATTERN.matcher(squash(rawName));
    return matcher.find() ? Optional.of(matcher.group(1) + matcher.group(2)) : Optional.empty();
  }
}
