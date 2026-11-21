package io.github.mridang.codegen.spec.parity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.ClassDescriptor;
import org.junit.jupiter.api.ClassOrdererContext;
import org.junit.jupiter.api.Test;

class ParityLastClassOrdererTest {

  static class GoClientSpec {}

  static class TestParitySpec {}

  static class RubyClientSpec {}

  private static ClassDescriptor descriptor(Class<?> type) {
    return new ClassDescriptor() {
      @Override
      public Class<?> getTestClass() {
        return type;
      }

      @Override
      public String getDisplayName() {
        return type.getSimpleName();
      }

      @Override
      public boolean isAnnotated(Class<? extends Annotation> annotationType) {
        return false;
      }

      @Override
      public <A extends Annotation> Optional<A> findAnnotation(Class<A> annotationType) {
        return Optional.empty();
      }

      @Override
      public <A extends Annotation> List<A> findRepeatableAnnotations(Class<A> annotationType) {
        return List.of();
      }
    };
  }

  @Test
  void shouldQueueTheComparisonAfterEveryOtherClassAndKeepTheRestInOrder() {
    List<ClassDescriptor> descriptors =
        new ArrayList<>(
            List.of(
                descriptor(TestParitySpec.class),
                descriptor(GoClientSpec.class),
                descriptor(RubyClientSpec.class)));
    ClassOrdererContext context =
        new ClassOrdererContext() {
          @Override
          public List<? extends ClassDescriptor> getClassDescriptors() {
            return descriptors;
          }

          @Override
          public Optional<String> getConfigurationParameter(String key) {
            return Optional.empty();
          }
        };

    new ParityLastClassOrderer().orderClasses(context);

    assertThat(descriptors.stream().map(d -> d.getTestClass().getSimpleName()).collect(Collectors.toList()))
        .containsExactly("GoClientSpec", "RubyClientSpec", "TestParitySpec");
  }
}
