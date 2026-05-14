package io.github.mridang.codegen.spec.swift;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class SwiftReservedWordsSpec extends AbstractReservedWordsSpec implements SwiftSpec {

    @Override
    public String getDockerImage() {
        return "swift:6.2";
    }

    @Override
    protected String getDumpScriptResource() {
        return "scripts/dump-swift-keywords.sh";
    }

    @Override
    protected String getKeywordsSourceResource() {
        return "scripts/swift_keywords.swift";
    }

    @Override
    protected String getKeywordsSourceContainerPath() {
        return "/scripts/swift_keywords.swift";
    }

    @Override
    protected String getReservedWordsResource() {
        return "/reserved-words/swift.txt";
    }
}
