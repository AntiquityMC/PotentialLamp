package juuxel.potentiallamp.gradle.task;

import juuxel.potentiallamp.gradle.util.PathUtil;
import juuxel.potentiallamp.gradle.util.TaskGroups;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

@UntrackedTask(because = "We want to run the tool")
public abstract class LaunchEnigma extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getGameJar();

    @InputFile
    public abstract RegularFileProperty getProfile();

    @OutputDirectory
    public abstract DirectoryProperty getMappingsDir();

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Inject
    protected abstract ExecOperations getExecOperations();

    public LaunchEnigma() {
        setGroup(TaskGroups.MAPPING_DEV);
    }

    @TaskAction
    protected void run() {
        getExecOperations().javaexec(spec -> {
            spec.getMainClass().set("cuchaz.enigma.gui.Main");
            spec.setClasspath(getClasspath());
            spec.args("-jar", PathUtil.get(getGameJar()).toAbsolutePath().toString());
            spec.args("-mappings", PathUtil.get(getMappingsDir()).toAbsolutePath().toString());
            spec.args("-profile", PathUtil.get(getProfile()).toAbsolutePath().toString());
            spec.setMaxHeapSize("2G");
        });
    }
}
