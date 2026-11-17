package io.github.mridang.codegen.spec.parity;

import java.util.Comparator;
import org.junit.jupiter.api.ClassDescriptor;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.ClassOrdererContext;

/**
 * Queues the comparison spec after every other class, so the client specs have written their
 * summaries by the time it runs. The other classes keep the order they had; the classes run
 * concurrently, so the comparison spec also waits for any client spec still marked as running.
 */
public final class ParityLastClassOrderer implements ClassOrderer {

  @Override
  public void orderClasses(ClassOrdererContext context) {
    context.getClassDescriptors().sort(Comparator.comparing(ParityLastClassOrderer::isComparison));
  }

  private static boolean isComparison(ClassDescriptor descriptor) {
    return "TestParitySpec".equals(descriptor.getTestClass().getSimpleName());
  }
}
