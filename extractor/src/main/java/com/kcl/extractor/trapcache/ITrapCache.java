package com.kcl.extractor.trapcache;

import com.kcl.extractor.ExtractorConfig;
import com.kcl.extractor.FileExtractor.FileType;
import com.semmle.util.exception.UserError;

import java.io.File;

import static com.kcl.extractor.ExtractorOptionsUtil.readExtractorOption;


/**
 * Generic TRAP cache interface.
 */
public interface ITrapCache {
    /**
     * Build a TRAP cache as defined by the extractor options, which are read from the corresponding
     * environment variables as defined in
     * https://github.com/github/codeql-core/blob/main/design/spec/codeql-extractors.md
     *
     * @return a TRAP cache
     */
    public static ITrapCache fromExtractorOptions() {
        String trapCachePath = readExtractorOption("trap", "cache", "dir");
        if (trapCachePath != null) {
            Long sizeBound = null;
            String trapCacheBound = readExtractorOption("trap", "cache", "bound");
            if (trapCacheBound != null) {
                sizeBound = DefaultTrapCache.asFileSize(trapCacheBound);
                if (sizeBound == null)
                    throw new UserError("Invalid TRAP cache size bound: " + trapCacheBound);
            }
            boolean writeable = true;
            String trapCacheWrite = readExtractorOption("trap", "cache", "write");
            if (trapCacheWrite != null) writeable = trapCacheWrite.equalsIgnoreCase("TRUE");
            return new DefaultTrapCache(trapCachePath, sizeBound, "1.0.0", writeable);
        }
        return new DummyTrapCache();
    }

    /**
     * Look up a file in the TRAP cache.
     *
     * @param source the content of the file
     * @param config the configuration options this file will be extracted with if it is not found in
     *               the cache
     * @param type   the type of the file
     * @return {@literal null} if this TRAP cache does not support caching the given file; otherwise,
     * a file in the TRAP cache which may either already exist (and then is guaranteed to hold
     * cached information), or does not yet exist (and should be populated by the extractor)
     */
    public File lookup(String source, ExtractorConfig config, FileType type);
}
