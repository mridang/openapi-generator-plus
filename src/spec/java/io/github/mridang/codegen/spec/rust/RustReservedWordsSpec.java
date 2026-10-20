package io.github.mridang.codegen.spec.rust;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class RustReservedWordsSpec extends AbstractReservedWordsSpec implements RustSpec {

    @Override
    protected String getDumpScriptResource() {
        return "scripts/dump-rust-keywords.sh";
    }

    @Override
    protected String getKeywordsSourceResource() {
        return "scripts/rust_keywords.rs";
    }

    @Override
    protected String getKeywordsSourceContainerPath() {
        return "/scripts/rust_keywords.rs";
    }

    @Override
    protected String getReservedWordsResource() {
        return "/reserved-words/rust.txt";
    }
}
