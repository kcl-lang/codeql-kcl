package com.kcl.extractor.parser;

import com.kcl.api.API;
import com.kcl.api.Spec;
import com.kcl.ast.Program;
import com.kcl.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.SneakyThrows;

public class ParserTest {
    @Test
    @SneakyThrows
    public void test() {
        // Create an instance of the API class
        API api = new API();

        Path testFile = Paths.get(getClass().getClassLoader().getResource("samples/a.k").toURI());

        System.out.println(testFile);

        // Parse the program by providing the file paths to the API
        Spec.ParseProgram_Result result = api.parseProgram(
                Spec.ParseProgram_Args.newBuilder().addPaths(testFile.toString()).build()
        );
        // Print the JSON representation of the AST (Abstract Syntax Tree)
        System.out.println(result.getAstJson());
        // Deserialize the JSON string into a Program object
        Program program = JsonUtil.deserializeProgram(result.getAstJson());

        System.out.println(program);
    }

}
