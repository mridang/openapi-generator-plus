package io.github.mridang.codegen.spec.fences;

/**
 * One fenced code block of a generated markdown file: the file it is in, the heading above it,
 * its position among the blocks of the SDK language, the kind declared on its info string and its
 * body.
 */
public final class Fence {

  private final String file;
  private final String heading;
  private final int number;
  private final int line;
  private final String kind;
  private final String body;

  Fence(String file, String heading, int number, int line, String kind, String body) {
    this.file = file;
    this.heading = heading;
    this.number = number;
    this.line = line;
    this.kind = kind;
    this.body = body;
  }

  /** The markdown file name, such as {@code README.md}. */
  public String file() {
    return file;
  }

  /** The text of the nearest heading above the fence, or an empty string. */
  public String heading() {
    return heading;
  }

  /** The 1-based position of the fence among the fences of the SDK language in its file. */
  public int number() {
    return number;
  }

  /** The 1-based line of the opening fence. */
  public int line() {
    return line;
  }

  /** The kind word after the language, {@code file} or {@code statements}, or empty. */
  public String kind() {
    return kind;
  }

  /** The text between the fence lines, each line ending in a newline. */
  public String body() {
    return body;
  }

  /** Where the fence is, for a failure message. */
  public String describe() {
    return file + ":" + line + " (fence " + number + ", under \"" + heading + "\")";
  }
}
