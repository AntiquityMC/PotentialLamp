package juuxel.potentiallamp.gradle.task;

import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class MapJar extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getInputJar();

    @InputFile
    public abstract RegularFileProperty getMappings();

    @Input
    public abstract Property<String> getSourceNamespace();

    @Input
    public abstract Property<String> getTargetNamespace();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    protected void remap() throws IOException {
        Path input = getInputJar().get().getAsFile().toPath();
        Path mappings = getMappings().get().getAsFile().toPath();
        Path output = getOutputJar().get().getAsFile().toPath();
        Files.deleteIfExists(output);

        TinyRemapper remapper = TinyRemapper.newRemapper()
            .withMappings(TinyUtils.createTinyMappingProvider(mappings, getSourceNamespace().get(), getTargetNamespace().get()))
            .renameInvalidLocals(true)
            .rebuildSourceFilenames(true)
            .build();

        try (var outputConsumer = new OutputConsumerPath.Builder(output).build()) {
            outputConsumer.addNonClassFiles(input);
            remapper.readInputs(input);
            remapper.apply(outputConsumer);
        } finally {
            remapper.finish();
        }
    }
}
