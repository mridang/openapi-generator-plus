package io.github.mridang.codegen.spec.elixir;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class ElixirReservedWordsSpec extends AbstractReservedWordsSpec implements ElixirSpec {

    @Override
    public String getDockerImage() {
        return "elixir:1.19";
    }

    @Override
    protected String getDumpScriptResource() {
        return "scripts/dump-elixir-keywords.sh";
    }

    @Override
    protected String getKeywordsSourceResource() {
        return "scripts/elixir_keywords.exs";
    }

    @Override
    protected String getKeywordsSourceContainerPath() {
        return "/scripts/elixir_keywords.exs";
    }

    @Override
    protected String getReservedWordsResource() {
        return "/reserved-words/elixir.txt";
    }
}
