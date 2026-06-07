package com.example.petstore.api.options;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.InputStream;

/** Options for the uploadPetCertificate operation. */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
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
