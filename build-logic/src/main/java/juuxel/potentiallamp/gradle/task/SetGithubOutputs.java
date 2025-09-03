package juuxel.potentiallamp.gradle.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Map;

public abstract class SetGithubOutputs extends DefaultTask {
    @Input
    public abstract MapProperty<String, String> getOutputProperties();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    public SetGithubOutputs() {
        String outputFile = System.getenv("GITHUB_OUTPUT");
        if (outputFile != null) {
            getOutputFile().convention(getProject().getLayout().file(getProject().provider(() -> new File(outputFile))));
        }
    }

    @TaskAction
    protected void setOutputs() throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(getOutputFile().get().getAsFile().toPath());
             PrintWriter pw = new PrintWriter(writer)) {
            for (Map.Entry<String, String> entry : getOutputProperties().get().entrySet()) {
                pw.printf("%s=%s%n", entry.getKey(), entry.getValue());
            }
        }
    }
}
