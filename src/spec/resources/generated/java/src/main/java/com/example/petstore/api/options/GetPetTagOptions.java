package com.example.petstore.api.options;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

/**
 * Options for the getPetTag operation.
 */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class GetPetTagOptions {
    @Nullable private List<String> colors;
    @Nullable private List<String> sizes;
    @Nullable private String filter;

    public GetPetTagOptions() {
    }

    public GetPetTagOptions colors(List<String> colors) {
        this.colors = colors;
        return this;
    }

    @Nullable
    public List<String> colors() {
        return colors;
    }

    public GetPetTagOptions sizes(List<String> sizes) {
        this.sizes = sizes;
        return this;
    }

    @Nullable
    public List<String> sizes() {
        return sizes;
    }

    public GetPetTagOptions filter(String filter) {
        this.filter = filter;
        return this;
    }

    @Nullable
    public String filter() {
        return filter;
    }
}
