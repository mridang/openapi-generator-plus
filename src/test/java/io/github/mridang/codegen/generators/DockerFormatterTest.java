package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Covers the bound on how many formatter containers run at once. */
class DockerFormatterTest {

  @Test
  @DisplayName("formatters run side by side but never more than the parallelism limit")
  void formattersRunConcurrentlyUpToTheLimit() throws InterruptedException {
    final int callers = DockerFormatter.PARALLELISM * 3;
    final AtomicInteger active = new AtomicInteger();
    final AtomicInteger peak = new AtomicInteger();
    final CountDownLatch done = new CountDownLatch(callers);
    for (int i = 0; i < callers; i++) {
      final Thread thread =
          new Thread(
              () -> {
                DockerFormatter.inSlot(
                    () -> {
                      peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                      try {
                        Thread.sleep(50);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      active.decrementAndGet();
                      return null;
                    });
                done.countDown();
              });
      thread.start();
    }
    done.await();
    assertTrue(peak.get() <= DockerFormatter.PARALLELISM, "peak " + peak.get());
    if (DockerFormatter.PARALLELISM > 1) {
      assertTrue(peak.get() > 1, "formatters should overlap, peak was " + peak.get());
    }
    assertEquals(0, active.get());
  }
}
