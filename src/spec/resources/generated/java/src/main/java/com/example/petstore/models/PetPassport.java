package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
@SuppressWarnings({"deprecation", "serial"})
public class PetPassport {

  /** Example: {@code null} */
  @JsonProperty("pet")
  @Nullable
  public Pet pet;

  /** Base64-encoded primary thumbnail */
  @JsonProperty("thumbnail")
  @Nullable
  public byte[] thumbnail;

  /**
   * Base64-encoded scans of each passport page
   *
   * <p>Example: {@code null}
   */
  @JsonProperty("scans")
  @Nullable
  public List<byte[]> scans = new ArrayList<>();

  /** Example: {@code null} */
  @JsonProperty("issuedAt")
  @Nullable
  public OffsetDateTime issuedAt;
}
