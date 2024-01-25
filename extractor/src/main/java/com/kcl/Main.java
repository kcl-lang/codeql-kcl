package com.kcl;

import com.kcl.extractor.AutoBuild;
import com.semmle.cli2.CodeQL;
import com.semmle.util.files.FileUtil;
import com.semmle.util.files.FileUtil8;
import com.semmle.util.io.ZipUtil;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        Path project = Path.of(System.getProperty("user.dir"));
        Path dataPath = project.resolve("data");
        Path finalisePath = dataPath.resolve("finalise");

        //extractor
        Path extractPath = dataPath.resolve("report");
        FileUtil8.recursiveDelete(extractPath);
        FileUtil.mkdirs(extractPath.toFile());
        Path trapPath = extractPath.resolve("trap");
        FileUtil.mkdirs(trapPath.toFile());
        Path extendPath = extractPath.resolve("extend");
        FileUtil.mkdirs(extendPath.toFile());
        Path sourcePath = extractPath.resolve("source");
        FileUtil.mkdirs(sourcePath.toFile());
        new AutoBuild().run();

        Path databasePath = dataPath.resolve("database");
        FileUtil8.recursiveDelete(databasePath);
        FileUtil.mkdirs(databasePath.toFile());

        Path projectPath = dataPath.resolve("project");
        Path zipPath = databasePath.resolve("src.zip");
        ZipUtil.zip(zipPath, true, null, Path.of("/"), projectPath);

        String databaseYaml = "codeql-database.yml";
        FileUtil.copy(finalisePath.resolve(databaseYaml).toFile(), databasePath.resolve(databaseYaml).toFile());
        String sourcePrefix = "sourceLocationPrefix: " + projectPath.toString();
        FileWriter fileWriter = new FileWriter(databasePath.resolve(databaseYaml).toFile(), true);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
        PrintWriter printWriter = new PrintWriter(bufferedWriter);
        printWriter.println(sourcePrefix);
        printWriter.flush();
        bufferedWriter.flush();
        fileWriter.flush();

        Path dbPath = databasePath.resolve("db-kcl");
        FileUtil.mkdirs(dbPath.toFile());

        Path dbschemePath = finalisePath.resolve("kcl.dbscheme");
        String[] importCmd = {"dataset", "import", dbPath.toString(), trapPath.toString(), "-S", dbschemePath.toString()};
        CodeQL.mainApi(importCmd);

        String[] measureCmd = {"dataset", "measure", dbPath.toString(), "-o", dbPath.resolve("kcl.dbscheme.stats").toString()};
        CodeQL.mainApi(measureCmd);
    }

}




