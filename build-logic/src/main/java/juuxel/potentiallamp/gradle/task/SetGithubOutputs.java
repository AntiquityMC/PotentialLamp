package juuxel.potentiallamp.gradle.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public abstract class SetGithubOutputs extends DefaultTask {
    @Input
    public abstract MapProperty<String, String> getOutputProperties();

    @TaskAction
    protected void setOutputs() {
        // TODO: switch to the non-deprecated way of doing this
        getOutputProperties().get().forEach((key ,value) -> {
            System.out.printf("::set-output name=%s::%s%n", key, value);
        });
    }
}
