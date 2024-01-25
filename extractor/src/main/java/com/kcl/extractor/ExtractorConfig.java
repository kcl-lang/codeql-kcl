package com.kcl.extractor;

import com.kcl.parser.KclAstParser;
import com.semmle.util.data.StringUtil;
import com.semmle.util.exception.UserError;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/**
 * Configuration options that affect the behaviour of the extractor.
 *
 * <p>The intended invariants are:
 *
 * <ol>
 *   <li>If the extractor is invoked twice on the same file (contents and path both the same) with
 *       the same configuration options, it will produce exactly the same TRAP files.
 *   <li>If the extractor is invoked on two files that have the same content, but whose path (and
 *       file extension) may be different, and the two invocations have the same configuration
 *       options, then the trap files it produces are identical from label #20000 onwards. (See
 *       comments on the trap cache below for further explanation.)
 * </ol>
 */
public class ExtractorConfig {
    ;
    /**
     * Is this code parsed as externs definitions?
     */
    private boolean externs;

    /**
     * Should parse errors be reported as violations instead of aborting extraction?
     */
    private boolean tolerateParseErrors;

    /**
     * Which {@link FileExtractor.FileType} should this code be parsed as?
     *
     * <p>If this is {@code null}, the file type is inferred from the file extension.
     */
    private String fileType;
    /**
     * Which {@link SourceType} should this code be parsed as?
     */
    private SourceType sourceType;
    /**
     * Should textual information be extracted into the lines/4 relation?
     */
    private boolean extractLines;
    /**
     * The default character encoding to use for parsing source files.
     */
    private String defaultEncoding;
    private VirtualSourceRoot virtualSourceRoot;

    public ExtractorConfig(boolean experimental) {
        this.sourceType = SourceType.KCL;
        this.tolerateParseErrors = true;
        this.defaultEncoding = StandardCharsets.UTF_8.name();
        this.virtualSourceRoot = VirtualSourceRoot.none;
    }

    public ExtractorConfig(ExtractorConfig that) {
        this.tolerateParseErrors = that.tolerateParseErrors;
        this.fileType = that.fileType;
        this.sourceType = that.sourceType;
        this.extractLines = that.extractLines;
        this.defaultEncoding = that.defaultEncoding;
        this.virtualSourceRoot = that.virtualSourceRoot;
    }

    public boolean isExterns() {
        return externs;
    }

    public boolean hasFileType() {
        return fileType != null;
    }

    public String getFileType() {
        return fileType;
    }

    public ExtractorConfig withFileType(String fileType) {
        ExtractorConfig res = new ExtractorConfig(this);
        res.fileType = fileType;
        return res;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public ExtractorConfig withSourceType(SourceType sourceType) {
        ExtractorConfig res = new ExtractorConfig(this);
        res.sourceType = sourceType;
        return res;
    }


    public boolean getExtractLines() {
        return extractLines;
    }

    public ExtractorConfig withExtractLines(boolean extractLines) {
        ExtractorConfig res = new ExtractorConfig(this);
        res.extractLines = extractLines;
        return res;
    }

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    public ExtractorConfig withDefaultEncoding(String defaultEncoding) {
        ExtractorConfig res = new ExtractorConfig(this);
        try {
            res.defaultEncoding = Charset.forName(defaultEncoding).name();
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new UserError("Unsupported encoding " + defaultEncoding + ".", e);
        }
        return res;
    }

    public VirtualSourceRoot getVirtualSourceRoot() {
        return virtualSourceRoot;
    }

    public ExtractorConfig withVirtualSourceRoot(VirtualSourceRoot virtualSourceRoot) {
        ExtractorConfig res = new ExtractorConfig(this);
        res.virtualSourceRoot = virtualSourceRoot;
        return res;
    }

    @Override
    public String toString() {
        return "ExtractorConfig ["
                + "tolerateParseErrors="
                + tolerateParseErrors
                + ", fileType="
                + fileType
                + ", sourceType="
                + sourceType
                + ", extractLines="
                + extractLines
                + ", defaultEncoding="
                + defaultEncoding
                + ", virtualSourceRoot="
                + virtualSourceRoot
                + "]";
    }


    public static enum SourceType {
        /**
         * Automatically determined source type.
         */
        KCL;

        @Override
        public String toString() {
            return StringUtil.lc(name());
        }

        /**
         * Gets the parser to use for parsing this source type.
         */
        public KclAstParser createParser(String input, int startPos) {
            switch (this) {
                default:
                    return new KclAstParser();
            }
        }
    }
}
