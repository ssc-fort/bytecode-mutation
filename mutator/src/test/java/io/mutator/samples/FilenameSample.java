package io.mutator.samples;
import org.apache.commons.io.FilenameUtils;
public class FilenameSample {
    public static String safeName(String path) {
        return FilenameUtils.getName(path);
    }
}
