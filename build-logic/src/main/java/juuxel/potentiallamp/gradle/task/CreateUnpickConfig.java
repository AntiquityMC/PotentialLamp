package juuxel.potentiallamp.gradle.task;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import juuxel.potentiallamp.gradle.util.PathUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;

public abstract class CreateUnpickConfig extends DefaultTask {
    @Input
    public abstract Property<String> getNamespace();

    @Input
    @Optional
    public abstract Property<String> getConstants();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    public CreateUnpickConfig() {
        getNamespace().convention("official");
    }

    @TaskAction
    protected void create() throws IOException {
        JsonObject config = new JsonObject();
        config.addProperty("version", 2);
        config.addProperty("namespace", getNamespace().get());
        if (getConstants().isPresent()) {
            config.addProperty("constants", getConstants().get());
        }

        try (Writer writer = Files.newBufferedWriter(PathUtil.get(getOutputFile()))) {
            new Gson().toJson(config, writer);
        }
    }
}
