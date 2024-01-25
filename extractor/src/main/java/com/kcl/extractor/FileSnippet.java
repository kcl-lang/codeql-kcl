package com.kcl.extractor;


import com.kcl.extractor.ExtractorConfig.SourceType;

import java.nio.file.Path;

/**
 * Denotes where a code snippet originated from within a file.
 */
public class FileSnippet {
    private Path originalFile;
    private int line;
    private int column;
    private TopLevelKind topLevelKind;
    private SourceType sourceType;

    public FileSnippet(Path originalFile, int line, int column, TopLevelKind topLevelKind, SourceType sourceType) {
        this.originalFile = originalFile;
        this.line = line;
        this.column = column;
        this.topLevelKind = topLevelKind;
        this.sourceType = sourceType;
    }

    public Path getOriginalFile() {
        return originalFile;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public TopLevelKind getTopLevelKind() {
        return topLevelKind;
    }

    public SourceType getSourceType() {
        return sourceType;
    }
}
