package io.github.mridang.codegen.spec.fences;

/** A file a fence harness writes into the container, relative to the SDK root. */
public final class HarnessFile {

  private final String path;
  private final String content;

  public HarnessFile(String path, String content) {
    this.path = path;
    this.content = content;
  }

  public String path() {
    return path;
  }

  public String content() {
    return content;
  }
}
