package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import javax.annotation.Nullable;

public class PhotoMetadata {

  @JsonProperty("caption")
  @Nullable
  public String caption;

  @JsonProperty("isPrimary")
  @Nullable
  public Boolean isPrimary;

  @JsonProperty("takenAt")
  @Nullable
  public OffsetDateTime takenAt;

  @JsonProperty("location")
  @Nullable
  public PhotoMetadataLocation location;
}
