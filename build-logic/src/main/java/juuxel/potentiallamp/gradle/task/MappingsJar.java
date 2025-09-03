package juuxel.potentiallamp.gradle.task;

import org.gradle.api.tasks.bundling.Jar;

public abstract class MappingsJar extends Jar {
    public MappingsJar() {
        getDestinationDirectory().set(getProject().getLayout().getBuildDirectory().dir("libs"));
    }

    public void mappings(Object file) {
        from(file, spec -> {
            spec.into("mappings");
            spec.rename(unused -> "mappings.tiny");
        });
    }
}
