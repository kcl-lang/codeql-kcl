package com.kcl.extractor;


import com.kcl.extractor.trapcache.CachingTrapWriter;
import com.kcl.extractor.trapcache.ITrapCache;
import com.semmle.util.data.StringUtil;
import com.semmle.util.exception.Exceptions;
import com.semmle.util.extraction.ExtractorOutputConfig;
import com.semmle.util.files.FileUtil;
import com.semmle.util.io.WholeIO;
import com.semmle.util.trap.TrapWriter;
import com.semmle.util.trap.TrapWriter.Label;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The file extractor extracts a single file and handles source archive population and TRAP caching;
 * it delegates to the appropriate {@link IExtractor} for extracting the contents of the file.
 */
public class FileExtractor {
    private final ExtractorConfig config;
    private final ExtractorOutputConfig outputConfig;
    private final ITrapCache trapCache;

    public FileExtractor(ExtractorConfig config, ExtractorOutputConfig outputConfig, ITrapCache trapCache) {
        this.config = config;
        this.outputConfig = outputConfig;
        this.trapCache = trapCache;
    }

    /**
     * Returns true if the byte sequence contains invalid UTF-8 or unprintable ASCII characters.
     */
    private static boolean hasUnprintableUtf8(byte[] bytes, int length) {
        // Constants for bytes with N high-order 1-bits.
        // They are typed as `int` as the subsequent byte-to-int promotion would
        // otherwise fill the high-order `int` bits with 1s.
        final int high1 = 0b10000000;
        final int high2 = 0b11000000;
        final int high3 = 0b11100000;
        final int high4 = 0b11110000;
        final int high5 = 0b11111000;

        int startIndex = skipBOM(bytes, length);
        for (int i = startIndex; i < length; ++i) {
            int b = bytes[i];
            if ((b & high1) == 0) { // 0xxxxxxx is an ASCII character
                // ASCII values 0-31 are unprintable, except 9-13 are whitespace.
                // 127 is the unprintable DEL character.
                if (b <= 8 || 14 <= b && b <= 31 || b == 127) {
                    return true;
                }
            } else {
                // Check for malformed UTF-8 multibyte code point
                int trailingBytes = 0;
                if ((b & high3) == high2) {
                    trailingBytes = 1; // 110xxxxx 10xxxxxx
                } else if ((b & high4) == high3) {
                    trailingBytes = 2; // 1110xxxx 10xxxxxx 10xxxxxx
                } else if ((b & high5) == high4) {
                    trailingBytes = 3; // 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                } else {
                    return true; // 10xxxxxx and 11111xxx are not valid here.
                }
                // Trailing bytes must be of form 10xxxxxx
                while (trailingBytes > 0) {
                    ++i;
                    --trailingBytes;
                    if (i >= length) {
                        return false;
                    }
                    if ((bytes[i] & high2) != high1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns the index after the initial BOM, if any, otherwise 0.
     */
    private static int skipBOM(byte[] bytes, int length) {
        if (length >= 2
                && (bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff
                || bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe)) {
            return 2;
        } else {
            return 0;
        }
    }

    public ExtractorConfig getConfig() {
        return config;
    }

    public boolean supports(File f) {
        return config.hasFileType() || FileType.forFile(f, config) != null;
    }

    /**
     * @return the number of lines of code extracted, or {@code null} if the file was cached
     */
    public ParseResultInfo extract(File f, ExtractorState state) throws IOException {
        FileSnippet snippet = state.getSnippets().get(f.toPath());
        if (snippet != null) {
            return this.extractSnippet(f.toPath(), snippet, state);
        }

        // populate source archive
        String source = new WholeIO(config.getDefaultEncoding()).strictread(f);
        outputConfig.getSourceArchive().add(f, source);

        // extract language-independent bits
        TrapWriter trapwriter = new TrapWriter(outputConfig.getTrapWriterFactory().getTrapFileFor(f));
        Label fileLabel = trapwriter.populateFile(f);

        LocationManager locationManager = new LocationManager(f, trapwriter, fileLabel);
        locationManager.emitFileLocation(fileLabel, 0, 0, 0, 0);

        // now extract the contents
        return extractContents(f, fileLabel, source, locationManager, state);
    }

    /**
     * Extract the contents of a file that is a snippet from another file.
     *
     * <p>A trap file will be derived from the snippet file, but its file label, source locations, and
     * source archive entry are based on the original file.
     */
    private ParseResultInfo extractSnippet(Path file, FileSnippet origin, ExtractorState state) throws IOException {
        TrapWriter trapwriter = new TrapWriter(outputConfig.getTrapWriterFactory().getTrapFileFor(file.toFile()));

        File originalFile = origin.getOriginalFile().toFile();
        Label fileLabel = trapwriter.populateFile(originalFile);
        LocationManager locationManager = new LocationManager(originalFile, trapwriter, fileLabel);
        locationManager.setStart(origin.getLine(), origin.getColumn());

        String source = new WholeIO(config.getDefaultEncoding()).strictread(file);

        return extractContents(file.toFile(), fileLabel, source, locationManager, state);
    }

    /**
     * Extract the contents of a file, potentially making use of cached information.
     *
     * <p>TRAP files can be logically split into two parts: a location-dependent prelude containing
     * all the `files`, `folders` and `containerparent` tuples, and a content-dependent main part
     * containing all the rest, which does not depend on the source file location at all. Locations in
     * the main part do, of course, refer to the source file's ID, but they do so via its symbolic
     * label, which is always #10000.
     *
     * <p>We only cache the content-dependent part, which makes up the bulk of the TRAP file anyway.
     * The location-dependent part is emitted from scratch every time by the {@link #extract(File,
     * ExtractorState)} method above.
     *
     * <p>In order to keep labels in the main part independent of the file's location, we bump the
     * TRAP label counter to a known value (currently 20000) after the location-dependent part has
     * been emitted. If the counter should already be larger than that (which is theoretically
     * possible with insanely deeply nested directories), we have to skip caching.
     *
     * <p>Also note that we support extraction with TRAP writer factories that are not file-backed;
     * obviously, no caching is done in that scenario.
     */
    private ParseResultInfo extractContents(
            File extractedFile, Label fileLabel, String source, LocationManager locationManager, ExtractorState state)
            throws IOException {
        ExtractionMetrics metrics = new ExtractionMetrics();
        metrics.startPhase(ExtractionMetrics.ExtractionPhase.FileExtractor_extractContents);
        metrics.setLength(source.length());
        metrics.setFileLabel(fileLabel);
        TrapWriter trapwriter = locationManager.getTrapWriter();
        FileType fileType = getFileType(extractedFile);

        File cacheFile = null, // the cache file for this extraction
                resultFile = null; // the final result TRAP file for this extraction

        if (bumpIdCounter(trapwriter)) {
            resultFile = outputConfig.getTrapWriterFactory().getTrapFileFor(extractedFile);
        }
        // check whether we can perform caching
        if (resultFile != null && fileType.isTrapCachingAllowed()) {
            cacheFile = trapCache.lookup(source, config, fileType);
        }

        boolean canUseCacheFile = cacheFile != null;
        boolean canReuseCacheFile = canUseCacheFile && cacheFile.exists();

        metrics.setCacheFile(cacheFile);
        metrics.setCanReuseCacheFile(canReuseCacheFile);
        metrics.writeDataToTrap(trapwriter);
        if (canUseCacheFile) {
            FileUtil.close(trapwriter);

            if (canReuseCacheFile) {
                FileUtil.append(cacheFile, resultFile);
                return null;
            }

            // not in the cache yet, so use a caching TRAP writer to
            // put the data into the cache and append it to the result file
            trapwriter = new CachingTrapWriter(cacheFile, resultFile);
            bumpIdCounter(trapwriter);
            // re-initialise the location manager, since it keeps a reference to the TRAP writer
            locationManager = new LocationManager(extractedFile, trapwriter, locationManager.getFileLabel());
        }

        // now do the extraction itself
        boolean successful = false;
        try {
            IExtractor extractor = fileType.mkExtractor(config, state);
            TextualExtractor textualExtractor = new TextualExtractor(trapwriter, locationManager, source, config.getExtractLines(), metrics, extractedFile);
            ParseResultInfo loc = extractor.extract(textualExtractor);
            int numLines = textualExtractor.isSnippet() ? 0 : textualExtractor.getNumLines();
            int linesOfCode = loc.getLinesOfCode(), linesOfComments = loc.getLinesOfComments();
            trapwriter.addTuple("numlines", fileLabel, numLines, linesOfCode, linesOfComments);
//            trapwriter.addTuple("filetype", fileLabel, fileType.toString());
            metrics.stopPhase(ExtractionMetrics.ExtractionPhase.FileExtractor_extractContents);
            metrics.writeTimingsToTrap(trapwriter);
            successful = true;
            return loc;
        } catch (Exception e) {
            System.out.println(e);
            return null;
        } finally {
            if (!successful && trapwriter instanceof CachingTrapWriter)
                ((CachingTrapWriter) trapwriter).discard();
            FileUtil.close(trapwriter);
        }
    }

    public FileType getFileType(File f) {
        return config.hasFileType()
                ? FileType.valueOf(config.getFileType())
                : FileType.forFile(f, config);
    }

    /**
     * Bump trap ID counter to separate path-dependent and path-independent parts of the TRAP file.
     *
     * @return true if the counter was successfully bumped
     */
    public boolean bumpIdCounter(TrapWriter trapwriter) {
        return trapwriter.bumpIdCount(20000);
    }

    /**
     * Information about supported file types.
     */
    public static enum FileType {

        KCL(".k") {
            @Override
            public IExtractor mkExtractor(ExtractorConfig config, ExtractorState state) {
                return new KclExtractor(config, state);
            }

            @Override
            public String toString() {
                return "kcl";
            }
        };

        /**
         * The names of all defined {@linkplain FileType}s.
         */
        public static final Set<String> allNames = new LinkedHashSet<>();
        /**
         * Number of bytes to read from the beginning of a file to sniff its file type.
         */
        private static final int fileHeaderSize = 128;

        static {
            for (FileType ft : FileType.values()) allNames.add(ft.name());
        }

        /**
         * The file extensions (lower-case, including leading dot) corresponding to this file type.
         */
        private final Set<String> extensions = new LinkedHashSet<>();

        private FileType(String... extensions) {
            this.extensions.addAll(Arrays.asList(extensions));
        }

        /**
         * Determine the {@link FileType} for a given file.
         */
        public static FileType forFile(File f, ExtractorConfig config) {
            String lcExt = StringUtil.lc(FileUtil.extension(f));
            for (FileType tp : values()) if (tp.contains(f, lcExt, config)) return tp;
            return null;
        }

        /**
         * Determine the {@link FileType} for a given file based on its extension only.
         */
        public static FileType forFileExtension(File f) {
            String lcExt = StringUtil.lc(FileUtil.extension(f));
            for (FileType tp : values())
                if (tp.getExtensions().contains(lcExt)) {
                    return tp;
                }
            return null;
        }

        /**
         * Computes if `f` is a binary file based on whether the initial `fileHeaderSize` bytes are printable UTF-8 chars.
         */
        public static boolean isBinaryFile(File f, String lcExt, ExtractorConfig config) {
            if (!config.getDefaultEncoding().equals(StandardCharsets.UTF_8.name())) {
                return false;
            }
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] bytes = new byte[fileHeaderSize];
                int length = fis.read(bytes);

                if (length == -1) return false;

                // Avoid invalid or unprintable UTF-8 files.
                if (hasUnprintableUtf8(bytes, length)) {
                    return true;
                }

                return false;
            } catch (IOException e) {
                Exceptions.ignore(e, "Let extractor handle this one.");
            }
            return false;
        }

        public Set<String> getExtensions() {
            return extensions;
        }

        /**
         * Construct an extractor for this file type with the appropriate configuration settings.
         */
        public abstract IExtractor mkExtractor(ExtractorConfig config, ExtractorState state);

        /**
         * Is the given file of this type?
         *
         * <p>For convenience, the lower-case file extension is also passed as an argument.
         */
        protected boolean contains(File f, String lcExt, ExtractorConfig config) {
            return extensions.contains(lcExt);
        }

        /**
         * Can we cache the TRAP output of this file?
         *
         * <p>Caching is disabled for TypeScript files as they depend on type information from other
         * files.
         */
        public boolean isTrapCachingAllowed() {
            return true;
        }
    }
}
