package com.example.petstore.api.options;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

/**
 * Options for the uploadPetDocument operation.
 */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class UploadPetDocumentOptions {
    private final InputStream file;
    @Nullable private String documentType;
    @Nullable private String notes;

    public UploadPetDocumentOptions(InputStream file) {
        this.file = file;
    }
    public InputStream file() {
        return file;
    }

    public UploadPetDocumentOptions documentType(String documentType) {
        this.documentType = documentType;
        return this;
    }

    @Nullable
    public String documentType() {
        return documentType;
    }

    public UploadPetDocumentOptions notes(String notes) {
        this.notes = notes;
        return this;
    }

    @Nullable
    public String notes() {
        return notes;
    }
}
