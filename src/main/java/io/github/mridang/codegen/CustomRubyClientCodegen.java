package io.github.mridang.codegen;

import org.openapitools.codegen.languages.RubyClientCodegen;

@SuppressWarnings("unused")
public class CustomRubyClientCodegen extends RubyClientCodegen {

    @Override
    public String getName() {
        return "custom-ruby";
    }

    @Override
    public String getHelp() {
        return "Generates a custom Ruby client library.";
    }
}
