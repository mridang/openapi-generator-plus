package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class PetPassport {

  @JsonProperty("pet")
  @Nullable
  public Pet pet;

  /** Base64-encoded primary thumbnail */
  @JsonProperty("thumbnail")
  @Nullable
  public byte[] thumbnail;

  /** Base64-encoded scans of each passport page */
  @JsonProperty("scans")
  @Nullable
  public List<byte[]> scans = new ArrayList<>();

  @JsonProperty("issuedAt")
  @Nullable
  public OffsetDateTime issuedAt;
}
