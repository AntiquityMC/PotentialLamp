package juuxel.potentiallamp.gradle.task;

import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Reader;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Writer;
import juuxel.potentiallamp.gradle.util.PathUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public abstract class MergeUnpickDefinitions extends DefaultTask {
    @InputFiles
    public abstract ConfigurableFileCollection getDefinitions();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    protected void merge() throws IOException {
        UnpickV3Writer writer = new UnpickV3Writer();

        for (File input : getDefinitions()) {
            try (var reader = new FileReader(input);
                 var unpickReader = new UnpickV3Reader(reader)) {
                unpickReader.accept(writer);
            }
        }

        Files.writeString(PathUtil.get(getOutputFile()), writer.getOutput(), StandardCharsets.UTF_8);
    }
}
