package com.example.petstore.api.options;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import javax.annotation.Nullable;

/** Options for the findPetsByStatus operation. */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class FindPetsByStatusOptions {
  @Nullable private String status;
  @Nullable private Map<String, String> filter;

  public FindPetsByStatusOptions() {}

  public FindPetsByStatusOptions status(String status) {
    this.status = status;
    return this;
  }

  @Nullable
  public String status() {
    return status;
  }

  public FindPetsByStatusOptions filter(Map<String, String> filter) {
    this.filter = filter;
    return this;
  }

  @Nullable
  public Map<String, String> filter() {
    return filter;
  }
}
