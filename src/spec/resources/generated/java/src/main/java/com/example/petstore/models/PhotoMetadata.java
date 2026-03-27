package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import javax.annotation.Nullable;

@SuppressWarnings({"deprecation", "serial"})
public class PhotoMetadata {

  /** Example: {@code null} */
  @JsonProperty("caption")
  @Nullable
  public String caption;

  /** Example: {@code null} */
  @JsonProperty("isPrimary")
  @Nullable
  public Boolean isPrimary;

  /** Example: {@code null} */
  @JsonProperty("takenAt")
  @Nullable
  public OffsetDateTime takenAt;

  /** Example: {@code null} */
  @JsonProperty("location")
  @Nullable
  public PhotoMetadataLocation location;
}
