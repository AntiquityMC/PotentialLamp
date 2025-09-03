package juuxel.potentiallamp.gradle.task;

import cuchaz.enigma.command.MapSpecializedMethodsCommand;
import cuchaz.enigma.translation.mapping.serde.MappingParseException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

public abstract class MapSpecializedMethods extends DefaultTask {
    @InputDirectory
    public abstract DirectoryProperty getInputMappings();

    @InputFile
    public abstract RegularFileProperty getGameJar();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    protected void run() throws IOException, MappingParseException {
        MapSpecializedMethodsCommand.run(
            getGameJar().get().getAsFile().toPath(),
            "enigma",
            getInputMappings().get().getAsFile().toPath(),
            "tinyv2:intermediary:named",
            getOutputFile().get().getAsFile().toPath()
        );
    }
}
