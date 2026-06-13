package com.example.petstore.api.options;

import java.io.InputStream;

/** Options for the uploadPetCertificate operation. */
public final class UploadPetCertificateOptions {
  private final InputStream file;

  /**
   * Creates an options instance with the required parameters.
   *
   * @param file the {@code file} parameter
   */
  public UploadPetCertificateOptions(InputStream file) {
    this.file = file;
  }

  /**
   * Returns the {@code file} parameter.
   *
   * @return the {@code file} parameter
   */
  public InputStream file() {
    return file;
  }
}
