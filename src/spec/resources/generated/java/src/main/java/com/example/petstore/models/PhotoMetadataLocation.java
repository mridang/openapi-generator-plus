package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
@SuppressWarnings({"deprecation", "serial"})
public class PhotoMetadataLocation {

  /** Example: {@code null} */
  @JsonProperty("lat")
  @Nullable
  public Double lat;

  /** Example: {@code null} */
  @JsonProperty("lng")
  @Nullable
  public Double lng;

}
