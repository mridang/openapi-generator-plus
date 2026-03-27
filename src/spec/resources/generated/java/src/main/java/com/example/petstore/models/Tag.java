package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

/**
 * Tags are deprecated, use categories instead
 *
 * @deprecated This schema is deprecated.
 */
@Deprecated
@SuppressWarnings({"deprecation", "serial"})
public class Tag {

  /** Example: {@code null} */
  @JsonProperty("id")
  @Nullable
  public Long id;

  /** Example: {@code null} */
  @JsonProperty("name")
  @Nullable
  public String name;
}
