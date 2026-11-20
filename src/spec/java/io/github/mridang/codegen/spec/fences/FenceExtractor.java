package io.github.mridang.codegen.spec.fences;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the fenced code blocks of one markdown file. Only a block whose first info-string word is
 * the SDK language is returned; blocks tagged {@code bash}, {@code toml}, {@code good} and the
 * like are not source and are skipped. The word after the language is the kind of the block
 * ({@code file} or {@code statements}); a block without one is returned with an empty kind, so
 * the caller can fail on it with the heading in the message. An opening fence that is never closed
 * fails loudly.
 */
public final class FenceExtractor {

  private FenceExtractor() {}

  /**
   * Extracts the fences of the given language from markdown text.
   *
   * @param file the file name, used in descriptions
   * @param markdown the markdown text
   * @param language the info-string language word that marks a block as SDK source
   * @return the blocks of that language, in file order
   */
  public static List<Fence> extract(String file, String markdown, String language) {
    List<Fence> fences = new ArrayList<>();
    String[] lines = markdown.split("\n", -1);
    String heading = "";
    int count = 0;
    int i = 0;
    while (i < lines.length) {
      String line = lines[i];
      if (line.startsWith("```")) {
        String info = line.substring(3).trim();
        int opened = i + 1;
        StringBuilder body = new StringBuilder();
        int j = i + 1;
        while (j < lines.length && !lines[j].startsWith("```")) {
          body.append(lines[j]).append('\n');
          j++;
        }
        if (j >= lines.length) {
          throw new IllegalStateException(
              file + ":" + opened + ": the fence opened here is never closed");
        }
        String[] words = info.isEmpty() ? new String[0] : info.split("\\s+");
        if (words.length > 0 && words[0].equals(language)) {
          count++;
          String kind = words.length > 1 ? words[1] : "";
          fences.add(new Fence(file, heading, count, opened, kind, body.toString()));
        }
        i = j + 1;
        continue;
      }
      if (line.startsWith("#")) {
        heading = line.replaceFirst("^#+\\s*", "").trim();
      }
      i++;
    }
    return fences;
  }
}
