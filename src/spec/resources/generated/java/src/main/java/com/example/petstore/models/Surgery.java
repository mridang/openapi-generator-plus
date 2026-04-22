package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
@SuppressWarnings({"deprecation", "serial"})
public class Surgery {

  /** Example: {@code null} */
  @JsonProperty("procedureName")
  public String procedureName;

  /** Example: {@code null} */
  @JsonProperty("durationMinutes")
  @Nullable
  public Integer durationMinutes;

  @SuppressWarnings("NullAway.Init")
  public Surgery() {}

  @com.fasterxml.jackson.annotation.JsonCreator
  public Surgery(@JsonProperty(value = "procedureName", required = true) String procedureName) {
    this.procedureName =
        java.util.Objects.requireNonNull(procedureName, "procedureName is required");
  }
}
