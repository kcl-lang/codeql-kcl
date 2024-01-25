package com.kcl.parser;

import com.kcl.api.API;
import com.kcl.api.Spec;

import java.nio.file.Path;

public class KclAstParser {
    public static Spec.LoadPackage_Result parse(Path input) throws Exception {
        API api = new API();
        return api.loadPackage(
                Spec.LoadPackage_Args.newBuilder().setResolveAst(true).setWithAstIndex(true).setParseArgs(
                                Spec.ParseProgram_Args.newBuilder().addPaths(input.toString()).build())
                        .build());
    }

}
