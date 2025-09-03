package juuxel.potentiallamp.gradle.util;

import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;

import java.nio.file.Path;

public final class PathUtil {
    public static Path get(Provider<? extends FileSystemLocation> location) {
        return location.get().getAsFile().toPath();
    }
}
