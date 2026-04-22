package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
@SuppressWarnings({"deprecation", "serial"})
public class Medication {

  /** Example: {@code null} */
  @JsonProperty("drugName")
  public String drugName;

  /** Example: {@code null} */
  @JsonProperty("dosage")
  @Nullable
  public String dosage;

  @SuppressWarnings("NullAway.Init")
  public Medication() {}

  @com.fasterxml.jackson.annotation.JsonCreator
  public Medication(@JsonProperty(value = "drugName", required = true) String drugName) {
    this.drugName = java.util.Objects.requireNonNull(drugName, "drugName is required");
  }
}
