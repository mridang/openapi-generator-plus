package com.example.petstore.models;

import com.example.petstore.models.PhotoMetadataLocation;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.OffsetDateTime;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
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
