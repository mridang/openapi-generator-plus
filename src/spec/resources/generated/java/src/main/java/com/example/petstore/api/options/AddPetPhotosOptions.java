package com.example.petstore.api.options;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.petstore.models.PhotoMetadata;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

/**
 * Options for the addPetPhotos operation.
 */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class AddPetPhotosOptions {
    private final List<InputStream> files;
    private final PhotoMetadata metadata;

    public AddPetPhotosOptions(List<InputStream> files, PhotoMetadata metadata) {
        this.files = files;
        this.metadata = metadata;
    }
    public List<InputStream> files() {
        return files;
    }
    public PhotoMetadata metadata() {
        return metadata;
    }
}
