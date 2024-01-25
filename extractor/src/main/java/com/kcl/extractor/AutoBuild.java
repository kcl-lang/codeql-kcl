package com.kcl.extractor;

import com.kcl.extractor.FileExtractor.FileType;
import com.kcl.extractor.trapcache.DummyTrapCache;
import com.kcl.extractor.trapcache.ITrapCache;
import com.kcl.parser.ParseError;
import com.semmle.util.data.StringUtil;
import com.semmle.util.diagnostic.DiagnosticLevel;
import com.semmle.util.diagnostic.DiagnosticLocation;
import com.semmle.util.diagnostic.DiagnosticWriter;
import com.semmle.util.exception.CatastrophicError;
import com.semmle.util.exception.Exceptions;
import com.semmle.util.exception.ResourceError;
import com.semmle.util.exception.UserError;
import com.semmle.util.extraction.ExtractorOutputConfig;
import com.semmle.util.files.FileUtil;
import com.semmle.util.process.Env;
import com.semmle.util.projectstructure.ProjectLayout;
import com.semmle.util.srcarchive.DefaultSourceArchive;
import com.semmle.util.trap.DefaultTrapWriterFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An alternative entry point to the JavaScript extractor.
 *
 * <p>It assumes the following environment variables to be set:
 *
 * <ul>
 *   <li><code>LGTM_SRC</code>: the source root;
 *   <li><code>SEMMLE_DIST</code>: the distribution root.
 * </ul>
 *
 * <p>Additionally, the following environment variables may be set to customise extraction
 * (explained in more detail below):
 *
 * <ul>
 *   <li><code>LGTM_INDEX_INCLUDE</code>: a newline-separated list of paths to include
 *   <li><code>LGTM_INDEX_EXCLUDE</code>: a newline-separated list of paths to exclude
 *   <li><code>LGTM_REPOSITORY_FOLDERS_CSV</code>: the path of a CSV file containing file
 *       classifications
 *   <li><code>LGTM_INDEX_FILTERS</code>: a newline-separated list of strings of form "include:PATTERN"
 *      or "exclude:PATTERN" that can be used to refine the list of files to include and exclude.
 *   <li><code>LGTM_INDEX_TYPESCRIPT</code>: whether to extract TypeScript
 *   <li><code>LGTM_INDEX_FILETYPES</code>: a newline-separated list of ".extension:filetype" pairs
 *       specifying which {@link FileType} to use for the given extension; the additional file type
 *       <code>XML</code> is also supported
 *   <li><code>LGTM_INDEX_XML_MODE</code>: whether to extract XML files
 *   <li><code>LGTM_THREADS</code>: the maximum number of files to extract in parallel
 * </ul>
 *
 * <p>It extracts the following:
 *
 * <ol>
 *   <li>all <code>*.js</code> files under <code>$SEMMLE_DIST/tools/data/externs</code>
 *   <li>all source code files (cf. {@link AutoBuild#extractSource()}.
 * </ol>
 *
 * <p>In the second step, the set of files to extract is determined in two phases: the walking
 * phase, which computes a set of candidate files, and the filtering phase. A file is extracted if
 * it is a candidate, its type is supported (cf. {@link FileExtractor#supports(File)}), and it is
 * not filtered out in the filtering phase.
 *
 * <p>The walking phase is parameterised by a set of <i>include paths</i> and a set of <i>exclude
 * paths</i>. By default, the single include path is <code>LGTM_SRC</code>. If the environment
 * variable <code>LGTM_INDEX_INCLUDE</code> is set, it is interpreted as a newline-separated list of
 * include paths, which are slash-separated paths relative to <code>LGTM_SRC</code>. This list
 * <i>replaces</i> (rather than extends) the default include path.
 *
 * <p>Similarly, the set of exclude paths is determined by the environment variables <code>
 * LGTM_INDEX_EXCLUDE</code> and <code>LGTM_REPOSITORY_FOLDERS_CSV</code>. The former is interpreted
 * like <code>LGTM_INDEX_EXCLUDE</code>, that is, a newline-separated list of exclude paths relative
 * to <code>LGTM_SRC</code>. The latter is interpreted as the path of a CSV file, where each line in
 * the file consists of a classification tag and an absolute path; any path classified as "external"
 * or "metadata" becomes an exclude path. Note that there are no implicit exclude paths.
 *
 * <p>The walking phase starts at each include path in turn and recursively traverses folders and
 * files. Symlinks and most hidden folders are skipped, but not hidden files. If it encounters a
 * sub-folder whose path is excluded, traversal stops. If it encounters a file, that file becomes a
 * candidate, unless its path is excluded. If the path of a file is both an include path and an
 * exclude path, the inclusion takes precedence, and the file becomes a candidate after all.
 *
 * <p>If an include or exclude path cannot be resolved, a warning is printed and the path is
 * ignored.
 *
 * <p>Note that the overall effect of this procedure is that the precedence of include and exclude
 * paths is derived from their specificity: a more specific include/exclude takes precedence over a
 * less specific include/exclude. In case of a tie, the include takes precedence.
 *
 * <p>The filtering phase is parameterised by a list of include/exclude patterns in the style of
 * {@link ProjectLayout} specifications. There are some built-in include/exclude patterns discussed
 * below. Additionally, the environment variable <code>LGTM_INDEX_FILTERS</code> is interpreted as a
 * newline-separated list of patterns to append to that list (hence taking precedence over the
 * built-in patterns). Unlike for {@link ProjectLayout}, patterns in <code>LGTM_INDEX_FILTERS</code>
 * use the syntax <code>include: pattern</code> for inclusions and <code>exclude: pattern</code> for
 * exclusions.
 *
 * <p>The default inclusion patterns cause the following files to be included:
 *
 * <p>The environment variable <code>LGTM_INDEX_FILETYPES</code> may be set to a newline-separated
 * list of file type specifications of the form <code>.extension:filetype</code>, causing all files
 * whose name ends in <code>.extension</code> to also be included by default.
 *
 * <p>The default exclusion patterns cause the following files to be excluded:
 *
 * <ul>
 *   <li>All JavaScript files whose name ends with <code>-min.js</code> or <code>.min.js</code>.
 *       Such files typically contain minified code. Since LGTM by default does not show results in
 *       minified files, it is not usually worth extracting them in the first place.
 * </ul>
 *
 * <p>JavaScript files are normally extracted with {@link ExtractorConfig.SourceType#KCL}, but an explicit source
 * type can be specified in the environment variable <code>LGTM_INDEX_SOURCE_TYPE</code>.
 *
 * <p>The file type as which a file is extracted can be customised via the <code>
 * LGTM_INDEX_FILETYPES</code> environment variable explained above.
 *
 * <p>If <code>LGTM_INDEX_XML_MODE</code> is set to <code>ALL</code>, then all files with extension
 * <code>.xml</code> under <code>LGTM_SRC</code> are extracted as XML (in addition to any files
 * whose file type is specified to be <code>XML</code> via <code>LGTM_INDEX_SOURCE_TYPE</code>).
 * Currently XML extraction does not respect inclusion and exclusion filters, but this is a bug, not
 * a feature, and hence will change eventually.
 *
 * <p>Note that all these customisations only apply to <code>LGTM_SRC</code>. Extraction of externs
 * is not customisable.
 *
 * <p>To customise the actual extraction (as opposed to determining which files to extract), the
 * following environment variables are available:
 *
 * <ul>
 *   <li><code>LGTM_THREADS</code> determines how many threads are used for parallel extraction of
 *       JavaScript files (TypeScript files cannot currently be extracted in parallel). If left
 *       unspecified, the extractor uses a single thread.
 *   <li><code>LGTM_TRAP_CACHE</code> and <code>LGTM_TRAP_CACHE_BOUND</code> can be used to specify
 *       the location and size of a trap cache to be used during extraction.
 * </ul>
 */
public class AutoBuild {
    /**
     * The default timeout when installing dependencies, in milliseconds.
     */
    public static final int INSTALL_DEPENDENCIES_DEFAULT_TIMEOUT = 10 * 60 * 1000; // 10 minutes
    /**
     * Compares files in the order they should be extracted.
     * <p>
     * The ordering of tsconfig.json files can affect extraction results. Since we
     * extract any given source file at most once, and a source file can be included from
     * multiple tsconfig.json files, we sometimes have to choose arbitrarily which tsconfig.json
     * to use for a given file (which is based on this ordering).
     * <p>
     * We sort them to help ensure reproducible extraction. Additionally, deeply nested files are
     * preferred over shallow ones to help ensure files are extracted with the most specific
     * tsconfig.json file.
     */
    public static final Comparator<Path> PATH_ORDERING = new Comparator<Path>() {
        public int compare(Path f1, Path f2) {
            if (f1.getNameCount() != f2.getNameCount()) {
                return f2.getNameCount() - f1.getNameCount();
            }
            return f1.compareTo(f2);
        }
    };
    /**
     * Like {@link #PATH_ORDERING} but for {@link File} objects.
     */
    public static final Comparator<File> FILE_ORDERING = new Comparator<File>() {
        public int compare(File f1, File f2) {
            return PATH_ORDERING.compare(f1.toPath(), f2.toPath());
        }
    };
    private final ExtractorOutputConfig outputConfig;
    private final ITrapCache trapCache;
    private final Map<String, FileExtractor.FileType> fileTypes = new LinkedHashMap<>();
    private final Set<Path> includes = new LinkedHashSet<>();
    private final Set<Path> excludes = new LinkedHashSet<>();
    private final Set<String> xmlExtensions = new LinkedHashSet<>();
    private final Path LGTM_SRC;
    private final String defaultEncoding;
    private final Path projectPath;
    private final VirtualSourceRoot virtualSourceRoot;
    private ProjectLayout filters;
    private ExecutorService threadPool;
    private volatile boolean seenCode = false;
    private volatile boolean seenFiles = false;
    private boolean installDependencies = false;
    private ExtractorState state;
    private AtomicInteger diagnosticCount = new AtomicInteger(0);
    private List<DiagnosticWriter> diagnosticsToClose = Collections.synchronizedList(new ArrayList<>());

    private ThreadLocal<DiagnosticWriter> diagnostics = new ThreadLocal<DiagnosticWriter>() {
        @Override
        protected DiagnosticWriter initialValue() {
            DiagnosticWriter result = initDiagnosticsWriter(diagnosticCount.incrementAndGet());
            diagnosticsToClose.add(result);
            return result;
        }
    };

    public AutoBuild() {
        this.projectPath = Path.of(System.getProperty("user.dir")).resolve("data");
        this.LGTM_SRC = toRealPath(projectPath.resolve("project"));
        DefaultTrapWriterFactory defaultTrapWriterFactory = new DefaultTrapWriterFactory(projectPath.resolve("report").resolve("trap").toString());
        DefaultSourceArchive defaultSourceArchive = new DefaultSourceArchive(projectPath.resolve("report").resolve("source").toString());
        this.outputConfig = new ExtractorOutputConfig(defaultTrapWriterFactory, defaultSourceArchive);
        this.trapCache = new DummyTrapCache();
        this.defaultEncoding = "utf8";
        this.virtualSourceRoot = makeVirtualSourceRoot();
        this.fileTypes.put(".k", FileType.KCL);
        setupMatchers();
        this.state = new ExtractorState();
    }

    /**
     * Returns an existing file named <code>dir/stem.ext</code> where <code>.ext</code> is any
     * of the given extensions, or <code>null</code> if no such file exists.
     */
    private static Path tryResolveWithExtensions(Path dir, String stem, Iterable<String> extensions) {
        for (String ext : extensions) {
            Path path = dir.resolve(stem + ext);
            if (Files.exists(dir.resolve(path))) {
                return path;
            }
        }
        return null;
    }


    /**
     * Gets a relative path from <code>from</code> to <code>to</code> provided
     * the latter is contained in the former. Otherwise returns <code>null</code>.
     *
     * @return a path or null
     */
    public static Path tryRelativize(Path from, Path to) {
        Path relative = from.relativize(to);
        if (relative.startsWith("..") || relative.isAbsolute()) {
            return null;
        }
        return relative;
    }

    public static void main(String[] args) {
        try {
            System.exit(new AutoBuild().run());
        } catch (IOException | UserError | CatastrophicError e) {
            System.err.println(e.toString());
            System.exit(1);
        }
    }

    protected VirtualSourceRoot makeVirtualSourceRoot() {
        return new VirtualSourceRoot(LGTM_SRC, projectPath.resolve("report").resolve("working"));
    }

    private String getEnvVar(String envVarName) {
        return getEnvVar(envVarName, null);
    }

    private String getEnvVar(String envVarName, String deflt) {
        String value = Env.systemEnv().getNonEmpty(envVarName);
        if (value == null) return deflt;
        return value;
    }

    private Path getPathFromEnvVar(String envVarName) {
        String lgtmSrc = getEnvVar(envVarName);
        if (lgtmSrc == null) throw new UserError(envVarName + " must be set.");
        Path path = Paths.get(lgtmSrc);
        return path;
    }

    private <T extends Enum<T>> T getEnumFromEnvVar(
            String envVarName, Class<T> enumClass, T defaultValue) {
        String envValue = getEnvVar(envVarName);
        if (envValue == null) return defaultValue;
        try {
            return Enum.valueOf(enumClass, StringUtil.uc(envValue));
        } catch (IllegalArgumentException ex) {
            Exceptions.ignore(ex, "We rewrite this to a meaningful user error.");
            Stream<String> enumNames =
                    Arrays.asList(enumClass.getEnumConstants()).stream()
                            .map(c -> StringUtil.lc(c.toString()));
            throw new UserError(
                    envVarName + " must be set to one of: " + StringUtil.glue(", ", enumNames.toArray()));
        }
    }

    /**
     * Convert {@code p} to a real path (as per {@link Path#toRealPath(java.nio.file.LinkOption...)}),
     * throwing a {@link ResourceError} if this fails.
     */
    private Path toRealPath(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            throw new ResourceError("Could not compute real path for " + p + ".", e);
        }
    }

    private void setupFileTypes() {
        fileTypes.put(".k", FileType.KCL);
    }


    /**
     * Set up include and exclude matchers based on environment variables.
     */
    private void setupMatchers() {
        includes.add(LGTM_SRC);
        setupFilters();
    }


    private void setupFilters() {
        List<String> patterns = new ArrayList<>();
        patterns.add("/");

        // exclude all files with extensions
        patterns.add("-**/*.*");

        // but include HTML, JavaScript, YAML and (optionally) TypeScript
        Set<FileType> defaultExtract = new LinkedHashSet<>();
        defaultExtract.add(FileType.KCL);
        for (FileType filetype : defaultExtract)
            for (String extension : filetype.getExtensions())
                patterns.add("**/*" + extension);

        // include any explicitly specified extensions
        for (String extension : fileTypes.keySet()) patterns.add("**/*" + extension);


        filters = new ProjectLayout(patterns.toArray(new String[0]));
    }

    /**
     * Add {@code pattern} to {@code patterns}, trimming off whitespace and prepending {@code base} to
     * it. If {@code pattern} ends with a trailing slash, that slash is stripped off.
     *
     * @return true if {@code pattern} is non-empty
     */
    private boolean addPathPattern(Set<Path> patterns, Path base, String pattern) {
        pattern = pattern.trim();
        if (pattern.isEmpty()) return false;
        Path path = base.resolve(pattern);
        try {
            Path realPath = toRealPath(path);
            patterns.add(realPath);
        } catch (ResourceError e) {
            Exceptions.ignore(e, "Ignore exception and print warning instead.");
            warn("Skipping path " + path + ", which does not exist.");
        }
        return true;
    }

    /**
     * Returns whether the autobuilder has seen code.
     * This is overridden in tests.
     */
    protected boolean hasSeenCode() {
        return seenCode;
    }

    /**
     * Perform extraction.
     */
    public int run() throws IOException {
        startThreadPool();
        try {
            CompletableFuture<?> sourceFuture = extractSource();
            sourceFuture.join(); // wait for source extraction to complete
        } catch (OutOfMemoryError oom) {
            System.err.println("Out of memory while extracting the project.");
            return 137; // the CodeQL CLI will interpret this as an out-of-memory error
            // purpusely not doing anything else (printing stack, etc.), as the JVM
            // basically guarantees nothing after an OOM
        } catch (RuntimeException | IOException e) {
            writeDiagnostics("Internal error: " + e, KCLDiagnosticKind.INTERNAL_ERROR);
            e.printStackTrace(System.err);
            return 1;
        } finally {
            shutdownThreadPool();
            diagnosticsToClose.forEach(DiagnosticWriter::close);
        }

//        if (!hasSeenCode()) {
//            if (seenFiles) {
//                warn("Only found KCL files that were empty or contained syntax errors.");
//            } else {
//                warn("No KCL code found.");
//            }
//            // ensuring that the finalize steps detects that no code was seen.
//            Path srcFolder = projectPath.resolve("report").resolve("src");
//            try {
//                // Non-recursive delete because "src/" should be empty.
//                FileUtil8.delete(srcFolder);
//            } catch (NoSuchFileException e) {
//                Exceptions.ignore(e, "the directory did not exist");
//            } catch (DirectoryNotEmptyException e) {
//                Exceptions.ignore(e, "just leave the directory if it is not empty");
//            }
//            return 0;
//        }
        return 0;
    }

    /**
     * Persist a diagnostic message to a file in the diagnostics directory.
     * See {@link KCLDiagnosticKind} for the kinds of errors that can be reported,
     * and see
     * {@link DiagnosticWriter} for more details.
     */
    public void writeDiagnostics(String message, KCLDiagnosticKind error) throws IOException {
        writeDiagnostics(message, error, null);
    }

    /**
     * Persist a diagnostic message with a location to a file in the diagnostics directory.
     * See {@link KCLDiagnosticKind} for the kinds of errors that can be reported,
     * and see
     * {@link DiagnosticWriter} for more details.
     */
    public void writeDiagnostics(String message, KCLDiagnosticKind error, DiagnosticLocation location) throws IOException {
        if (diagnostics.get() == null) {
            warn("No diagnostics directory, so not writing diagnostic: " + message);
            return;
        }

        // DiagnosticLevel level, String extractorName, String sourceId, String sourceName, String markdown
        diagnostics.get().writeMarkdown(error.getLevel(), "javascript", "js/" + error.getId(), error.getName(),
                message, location);
    }

    private DiagnosticWriter initDiagnosticsWriter(int count) {
        String diagnosticsDir = System.getenv("CODEQL_EXTRACTOR_JAVASCRIPT_DIAGNOSTIC_DIR");

        if (diagnosticsDir != null) {
            File diagnosticsDirFile = new File(diagnosticsDir);
            if (!diagnosticsDirFile.isDirectory()) {
                warn("Diagnostics directory " + diagnosticsDir + " does not exist");
            } else {
                File diagnosticsFile = new File(diagnosticsDirFile, "autobuilder-" + count + ".jsonl");
                try {
                    return new DiagnosticWriter(diagnosticsFile);
                } catch (FileNotFoundException e) {
                    warn("Failed to open diagnostics file " + diagnosticsFile);
                }
            }
        }
        return null;
    }

    private void startThreadPool() {
        int defaultNumThreads = 1;
        int numThreads = Env.systemEnv().getInt("LGTM_THREADS", defaultNumThreads);
        if (numThreads > 1) {
            System.out.println("Parallel extraction with " + numThreads + " threads.");
            threadPool = Executors.newFixedThreadPool(numThreads);
        } else {
            System.out.println("Single-threaded extraction.");
            threadPool = null;
        }
    }

    private void shutdownThreadPool() {
        if (threadPool != null) {
            threadPool.shutdown();
            try {
                threadPool.awaitTermination(365, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                Exceptions.ignore(e, "Awaiting termination is not essential.");
            }
        }
    }


    /**
     * Extract all supported candidate files that pass the filters.
     */
    private CompletableFuture<?> extractSource() throws IOException {
        // default extractor
        FileExtractor defaultExtractor = new FileExtractor(mkExtractorConfig(), outputConfig, trapCache);

        FileExtractors extractors = new FileExtractors(defaultExtractor);

        // custom extractor for explicitly specified file types
        for (Map.Entry<String, FileType> spec : fileTypes.entrySet()) {
            String extension = spec.getKey();
            String fileType = spec.getValue().name();
            ExtractorConfig extractorConfig = mkExtractorConfig().withFileType(fileType);
            extractors.customExtractors.put(extension, new FileExtractor(extractorConfig, outputConfig, trapCache));
        }

        Set<Path> filesToExtract = new LinkedHashSet<>();
        findFilesToExtract(defaultExtractor, filesToExtract);

        filesToExtract = filesToExtract.stream()
                .sorted(PATH_ORDERING)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Path> extractedFiles = new LinkedHashSet<>();

        // extract remaining files
        return extractFiles(filesToExtract, extractedFiles, extractors);
    }

    private CompletableFuture<?> extractFiles(
            Set<Path> filesToExtract,
            Set<Path> extractedFiles,
            FileExtractors extractors) {

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Path f : filesToExtract) {
            if (extractedFiles.contains(f))
                continue;
            extractedFiles.add(f);
            futures.add(extract(extractors.forFile(f), f, true));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }


    private ExtractorConfig mkExtractorConfig() {
        ExtractorConfig config = new ExtractorConfig(true);
        config = config.withSourceType(getSourceType());
        config = config.withVirtualSourceRoot(virtualSourceRoot);
        if (defaultEncoding != null) config = config.withDefaultEncoding(defaultEncoding);
        return config;
    }

    private void findFilesToExtract(FileExtractor extractor, final Set<Path> filesToExtract) throws IOException {
        Path[] currentRoot = new Path[1];
        FileVisitor<? super Path> visitor =
                new SimpleFileVisitor<Path>() {
                    private boolean isFileIncluded(Path file) {
                        // normalise path for matching
                        String path = file.toString().replace('\\', '/');
                        if (path.charAt(0) != '/') path = "/" + path;
                        return filters.includeFile(path);
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        if (!attrs.isRegularFile() && !attrs.isDirectory()) return FileVisitResult.SKIP_SUBTREE;

                        if (!file.equals(currentRoot[0]) && excludes.contains(file))
                            return FileVisitResult.SKIP_SUBTREE;

                        // extract files that are supported and pass the include/exclude patterns
                        boolean supported = extractor.supports(file.toFile());
                        if (!supported && !fileTypes.isEmpty()) {
                            supported = fileTypes.containsKey(FileUtil.extension(file));
                        }
                        if (supported && isFileIncluded(file)) {
                            filesToExtract.add(normalizePath(file));
                        }

                        return super.visitFile(file, attrs);
                    }

                    /**
                     * Returns {@code true} if {@code dir} is a hidden directory
                     * that should be skipped by default.
                     */
                    private boolean isSkippedHiddenDirectory(Path dir) {
                        // Allow .github folders as they may contain YAML files relevant to GitHub repositories.
                        return dir.toFile().isHidden() && !dir.getFileName().toString().equals(".github");
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (!dir.equals(currentRoot[0]) && (excludes.contains(dir) || isSkippedHiddenDirectory(dir)))
                            return FileVisitResult.SKIP_SUBTREE;
                        if (Files.exists(dir.resolve("codeql-database.yml"))) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return super.preVisitDirectory(dir, attrs);
                    }
                };
        for (Path root : includes) {
            currentRoot[0] = root;
            Files.walkFileTree(currentRoot[0], visitor);
        }
    }


    private Path normalizePath(Path path) {
        return path.toAbsolutePath().normalize();
    }


    /**
     * Get the source type specified in <code>LGTM_INDEX_SOURCE_TYPE</code>, or the default of {@link
     * ExtractorConfig.SourceType#KCL}.
     */
    private ExtractorConfig.SourceType getSourceType() {
        String sourceTypeName = getEnvVar("LGTM_INDEX_SOURCE_TYPE");
        if (sourceTypeName != null) {
            try {
                return ExtractorConfig.SourceType.valueOf(StringUtil.uc(sourceTypeName));
            } catch (IllegalArgumentException e) {
                Exceptions.ignore(e, "We construct a better error message.");
                throw new UserError(sourceTypeName + " is not a valid source type.");
            }
        }
        return ExtractorConfig.SourceType.KCL;
    }

    /**
     * Extract a single file using the given extractor and state.
     *
     * <p>If the state is {@code null}, the extraction job will be submitted to the {@link
     * #threadPool}, otherwise extraction will happen on the main thread.
     */
    protected CompletableFuture<?> extract(FileExtractor extractor, Path file, boolean concurrent) {
        if (concurrent && threadPool != null) {
            return CompletableFuture.runAsync(() -> doExtract(extractor, file, state), threadPool);
        } else {
            doExtract(extractor, file, state);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void doExtract(FileExtractor extractor, Path file, ExtractorState state) {
        File f = file.toFile();
        if (!f.exists()) {
            warn("Skipping " + file + ", which does not exist.");
            return;
        }

        try {
            long start = logBeginProcess("Extracting " + file);
            ParseResultInfo loc = extractor.extract(f, state);
//            if (!extractor.getConfig().isExterns() && (loc == null || loc.getLinesOfCode() != 0)) seenCode = true;
//            if (!extractor.getConfig().isExterns()) seenFiles = true;
            List<ParseError> errors = loc == null ? Collections.emptyList() : loc.getParseErrors();
            for (ParseError err : errors) {
                String msg = "A parse error occurred: " + StringUtil.quoteWithBackticks(err.getMessage().trim())
                        + ".";

                Optional<DiagnosticLocation> diagLoc = Optional.empty();
                if (file.startsWith(LGTM_SRC)) {
                    diagLoc = DiagnosticLocation.builder()
                            .setFile(file.subpath(LGTM_SRC.getNameCount(), file.getNameCount()).toString()) // file, relative to the source root
                            .setStartLine(err.getPosition().getLine())
                            .setStartColumn(err.getPosition().getColumn() + 1) // convert from 0-based to 1-based
                            .setEndLine(err.getPosition().getLine())
                            .setEndColumn(err.getPosition().getColumn() + 1) // convert from 0-based to 1-based
                            .build()
                            .getOk();
                }
                if (diagLoc.isPresent()) {
                    msg += " Check the syntax of the file. If the file is invalid, correct the error or "
                            + "[exclude](https://docs.github.com/en/code-security/code-scanning/automatically-scanning-"
                            + "your-code-for-vulnerabilities-and-errors/customizing-code-scanning) the file from analysis.";
                    writeDiagnostics(msg, KCLDiagnosticKind.PARSE_ERROR, diagLoc.get());
                } else {
                    msg += " The affected file is not located within the code being analyzed."
                            + (Env.systemEnv().isActions() ? " Please see the workflow run logs for more information." : "");
                    writeDiagnostics(msg, KCLDiagnosticKind.PARSE_ERROR);
                }
            }
            logEndProcess(start, "Done extracting " + file);
        } catch (OutOfMemoryError oom) {
            System.err.println("Out of memory while extracting a file.");
            System.exit(137); // caught by the CodeQL CLI
        } catch (Throwable t) {
            System.err.println("Exception while extracting " + file + ".");
            t.printStackTrace(System.err);
            try {
                writeDiagnostics("Internal error: " + t, KCLDiagnosticKind.INTERNAL_ERROR);
            } catch (IOException ignored) {
                Exceptions.ignore(ignored, "we are already crashing");
            }
            System.exit(1);
        }
    }

    private void warn(String msg) {
        System.err.println(msg);
        System.err.flush();
    }

    private long logBeginProcess(String message) {
        System.out.println(message);
        return System.nanoTime();
    }

    private void logEndProcess(long timedLogMessageStart, String message) {
        long end = System.nanoTime();
        int milliseconds = (int) ((end - timedLogMessageStart) / 1_000_000);
        System.out.println(message + " (" + milliseconds + " ms)");
        System.out.flush();
    }


    /**
     * A kind of error that can happen during extraction of JavaScript or TypeScript
     * code.
     * For use with the {@link #writeDiagnostics(String, KCLDiagnosticKind)} method.
     */
    public static enum KCLDiagnosticKind {
        PARSE_ERROR("parse-error", "Could not process some files due to syntax errors", DiagnosticLevel.Warning),
        INTERNAL_ERROR("internal-error", "Internal error", DiagnosticLevel.Debug);

        private final String id;
        private final String name;
        private final DiagnosticLevel level;

        private KCLDiagnosticKind(String id, String name, DiagnosticLevel level) {
            this.id = id;
            this.name = name;
            this.level = level;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public DiagnosticLevel getLevel() {
            return level;
        }
    }

    public class FileExtractors {
        FileExtractor defaultExtractor;
        Map<String, FileExtractor> customExtractors = new LinkedHashMap<>();

        FileExtractors(FileExtractor defaultExtractor) {
            this.defaultExtractor = defaultExtractor;
        }

        public FileExtractor forFile(Path f) {
            return customExtractors.getOrDefault(FileUtil.extension(f), defaultExtractor);
        }

        public FileType fileType(Path f) {
            return forFile(f).getFileType(f.toFile());
        }
    }
}
