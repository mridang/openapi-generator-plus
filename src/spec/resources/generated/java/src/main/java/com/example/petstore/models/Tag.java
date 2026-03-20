package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

/**
 * Tags are deprecated, use categories instead
 *
 * @deprecated This schema is deprecated.
 */
@Deprecated
@SuppressWarnings("deprecation")
public class Tag {

  @JsonProperty("id")
  @Nullable
  public Long id;

  @JsonProperty("name")
  @Nullable
  public String name;
}
