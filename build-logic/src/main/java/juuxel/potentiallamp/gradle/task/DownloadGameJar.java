package juuxel.potentiallamp.gradle.task;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.NoSuchElementException;

public abstract class DownloadGameJar extends DefaultTask {
    private static final String CLIENT_DOWNLOAD = "client";

    @Input
    public abstract Property<String> getGameVersion();

    @InputFile
    public abstract RegularFileProperty getVersionManifest();

    @OutputFile
    public abstract RegularFileProperty getGameJar();

    @TaskAction
    protected void download() throws IOException {
        Path versionManifestPath = getVersionManifest().get().getAsFile().toPath();
        Path gameJarPath = getGameJar().get().getAsFile().toPath();
        String gameVersion = getGameVersion().get();

        Gson gson = new Gson();
        JsonObject manifest;
        try (BufferedReader reader = Files.newBufferedReader(versionManifestPath)) {
            manifest = gson.fromJson(reader, JsonObject.class);
        }

        String versionInfoUrl = findVersionInfoUrl(manifest, gameVersion);
        JsonObject versionInfo;
        try (InputStream in = URI.create(versionInfoUrl).toURL().openStream();
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            versionInfo = gson.fromJson(reader, JsonObject.class);
        }

        String clientUrl = findDownloadUrl(versionInfo, CLIENT_DOWNLOAD);
        try (InputStream in = URI.create(clientUrl).toURL().openStream()) {
            Files.copy(in, gameJarPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String findVersionInfoUrl(JsonObject manifest, String version) {
        for (JsonElement entry : manifest.getAsJsonArray("versions")) {
            JsonObject versionData = entry.getAsJsonObject();

            if (version.equals(versionData.getAsJsonPrimitive("id").getAsString())) {
                return versionData.getAsJsonPrimitive("url").getAsString();
            }
        }

        throw new NoSuchElementException("Could not find version info URL for version " + version);
    }

    private static String findDownloadUrl(JsonObject versionInfo, String download) {
        return versionInfo
            .getAsJsonObject("downloads")
            .getAsJsonObject(download)
            .getAsJsonPrimitive("url")
            .getAsString();
    }
}
