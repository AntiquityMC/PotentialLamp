package juuxel.potentiallamp.gradle.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public abstract class DownloadVersionManifest extends DefaultTask {
    private static final String URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    @OutputFile
    public abstract RegularFileProperty getTarget();

    @TaskAction
    protected void download() throws IOException {
        Path targetPath = getTarget().get().getAsFile().toPath();
        Files.createDirectories(targetPath.getParent());

        try (InputStream in = URI.create(URL).toURL().openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
