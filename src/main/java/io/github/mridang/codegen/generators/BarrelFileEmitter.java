package io.github.mridang.codegen.generators;

import java.util.List;
import java.util.Map;

/**
 * Implemented by language codegens that need to emit barrel/index/mod
 * files aggregating all per-operation Options class exports. Only Python,
 * Node, Dart, and Rust use this mechanism; the others either have no
 * barrel convention or handle it via a different template.
 *
 * <p>The {@link AbstractBetterCodegen#writeOptionsBarrelFiles} hook calls
 * {@link #emitBarrelFiles} on any codegen that implements this interface,
 * passing the accumulated metadata for all generated Options files.
 */
public interface BarrelFileEmitter {

    /**
     * Writes the language-specific barrel/index/mod file(s) that re-export
     * or aggregate all generated Options classes.
     *
     * @param optionsFiles metadata maps for each generated Options file;
     *                     each map contains at minimum {@code "optionsClassName"}
     *                     and any language-specific keys added by
     *                     {@code enrichOptionsMetadata}.
     */
    void emitBarrelFiles(List<Map<String, String>> optionsFiles);
}
