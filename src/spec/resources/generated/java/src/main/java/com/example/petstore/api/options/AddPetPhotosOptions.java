package com.example.petstore.api.options;

import com.example.petstore.models.PhotoMetadata;
import java.io.InputStream;
import java.util.List;

/** Options for the addPetPhotos operation. */
public final class AddPetPhotosOptions {
  private final List<InputStream> files;
  private final PhotoMetadata metadata;

  /**
   * Creates an options instance with the required parameters.
   *
   * @param files the {@code files} parameter
   * @param metadata the {@code metadata} parameter
   */
  public AddPetPhotosOptions(List<InputStream> files, PhotoMetadata metadata) {
    this.files = java.util.List.copyOf(files);
    this.metadata = metadata;
  }

  /**
   * Returns the {@code files} parameter.
   *
   * @return the {@code files} parameter
   */
  public List<InputStream> files() {
    return files;
  }

  /**
   * Returns the {@code metadata} parameter.
   *
   * @return the {@code metadata} parameter
   */
  public PhotoMetadata metadata() {
    return metadata;
  }
}
