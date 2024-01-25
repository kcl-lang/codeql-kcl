package com.kcl.extractor;

import com.semmle.util.process.Env;

public class ExtractorOptionsUtil {
    public static String readExtractorOption(String... option) {
        StringBuilder name = new StringBuilder("EXTRACTOR_KCL_OPTION");
        for (String segment : option)
            name.append("_").append(segment.toUpperCase());
        return Env.systemEnv().getNonEmpty(name.toString());
    }
}
