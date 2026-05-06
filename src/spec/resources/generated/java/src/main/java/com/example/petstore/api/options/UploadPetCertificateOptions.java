package com.example.petstore.api.options;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

/**
 * Options for the uploadPetCertificate operation.
 */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class UploadPetCertificateOptions {
    private final InputStream file;

    public UploadPetCertificateOptions(InputStream file) {
        this.file = file;
    }
    public InputStream file() {
        return file;
    }
}
