package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

@SuppressWarnings({"deprecation", "serial"})
public class PetPassport {

  /** Example: {@code null} */
  @JsonProperty("pet")
  @Nullable
  public Pet pet;

  /**
   * Base64-encoded primary thumbnail
   *
   * <p>Example: {@code [B@67943949}
   */
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
