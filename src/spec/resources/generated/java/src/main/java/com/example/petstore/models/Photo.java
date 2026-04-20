package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
@SuppressWarnings({"deprecation", "serial"})
public class Photo {

  /** Example: {@code null} */
  @JsonProperty("id")
  @Nullable
  public Long id;

  /** Example: {@code null} */
  @JsonProperty("caption")
  @Nullable
  public String caption;

  /** Example: {@code null} */
  @JsonProperty("isPrimary")
  @Nullable
  public Boolean isPrimary;

  /** Example: {@code null} */
  @JsonProperty("url")
  @Nullable
  public String url;

}
