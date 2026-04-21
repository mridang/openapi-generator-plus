package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
@SuppressWarnings({"deprecation", "serial"})
public class Category {

  /** Example: {@code 1} */
  @JsonProperty("id")
  @Nullable
  public Long id;

  /** Example: {@code Dogs} */
  @JsonProperty("name")
  @Nullable
  public String name;
}
