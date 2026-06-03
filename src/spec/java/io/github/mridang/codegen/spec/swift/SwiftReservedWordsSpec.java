package io.github.mridang.codegen.spec.swift;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class SwiftReservedWordsSpec extends AbstractReservedWordsSpec implements SwiftSpec {

    @Override
    public String getDockerImage() {
        return "swift:6.2@sha256:4e50a9e711e8682a8c42bacfeed204568adfd6985a63b3789a165f28d296a28a";
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
