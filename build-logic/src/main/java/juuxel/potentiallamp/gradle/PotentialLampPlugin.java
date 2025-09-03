package juuxel.potentiallamp.gradle;

import juuxel.potentiallamp.gradle.task.CompleteNames;
import juuxel.potentiallamp.gradle.task.DownloadGameJar;
import juuxel.potentiallamp.gradle.task.DownloadVersionManifest;
import juuxel.potentiallamp.gradle.task.GenerateObfToNamedTiny;
import juuxel.potentiallamp.gradle.task.MapJar;
import juuxel.potentiallamp.gradle.task.MapSpecializedMethods;
import juuxel.potentiallamp.gradle.task.MappingsJar;
import juuxel.potentiallamp.gradle.util.TaskGroups;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Delete;

public final class PotentialLampPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply("base");
        var extension = project.getExtensions().create("potentialLamp", PotentialLampExtension.class);

        project.getTasks().named(BasePlugin.CLEAN_TASK_NAME, Delete.class, task -> {
            task.delete(extension.getCacheDir());
        });

        var downloadVersionManifest = project.getTasks().register("downloadVersionManifest", DownloadVersionManifest.class, task -> {
            task.setGroup(TaskGroups.JAR_SETUP);
            task.getTarget().set(extension.getCacheFile("version_manifest.json"));
        });
        var downloadGameJar = project.getTasks().register("downloadGameJar", DownloadGameJar.class, task -> {
            task.setGroup(TaskGroups.JAR_SETUP);
            task.getGameVersion().set(extension.getMinecraftVersion());
            task.getGameJar().set(extension.getCacheFile(task.getGameVersion().map(ver -> ver + ".jar")));
            task.getVersionManifest().set(downloadVersionManifest.flatMap(DownloadVersionManifest::getTarget));
        });
        var mapIntermediaryJar = project.getTasks().register("mapIntermediaryJar", MapJar.class, task -> {
            task.setGroup(TaskGroups.JAR_SETUP);
            task.getInputJar().set(downloadGameJar.flatMap(DownloadGameJar::getGameJar));
            task.getOutputJar().set(extension.getCacheFile(extension.getMinecraftVersion().map(ver -> ver + "-intermediary.jar")));
            task.getSourceNamespace().set("official");
            task.getTargetNamespace().set("intermediary");
            task.getMappings().set(extension.getIntermediaryFile());
        });
        var mapSpecializedMethods = project.getTasks().register("mapSpecializedMethods", MapSpecializedMethods.class, task -> {
            task.setGroup(TaskGroups.MAPPING_BUILD);
            task.getInputMappings().set(extension.getMappingsDir());
            task.getGameJar().set(mapIntermediaryJar.flatMap(MapJar::getOutputJar));
            task.getOutputFile().set(extension.getCacheFile("built_mappings.tiny"));
        });
        var completeNames = project.getTasks().register("completeNames", CompleteNames.class, task -> {
            task.setGroup(TaskGroups.MAPPING_BUILD);
            task.getInputFile().set(mapSpecializedMethods.flatMap(MapSpecializedMethods::getOutputFile));
            task.getIntermediaryGameJar().set(mapIntermediaryJar.flatMap(MapJar::getOutputJar));
            task.getOutputFile().set(extension.getCacheFile("completed_mappings.tiny"));
        });

        registerObfToNamedTiny(project, extension, completeNames.flatMap(CompleteNames::getOutputFile), "", "official");
        registerObfToNamedTiny(project, extension, completeNames.flatMap(CompleteNames::getOutputFile), "I", "intermediary");
    }

    private static void registerObfToNamedTiny(Project project, PotentialLampExtension extension, Provider<RegularFile> mappings, String variant, String fallbackNamespace) {
        var mappingTask = project.getTasks().register("generateObfToNamed" + variant + "Tiny", GenerateObfToNamedTiny.class, task -> {
            task.setGroup(TaskGroups.MAPPING_BUILD);
            task.getFallbackNamespace().set(fallbackNamespace);
            task.getIntermediary().set(extension.getIntermediaryFile());
            task.getMappings().set(mappings);
            task.getOutput().set(project.getLayout().getBuildDirectory().file("obfToNamed" + variant + ".tiny"));
        });
        var jarTask = project.getTasks().register("obfToNamed" + variant + "TinyJar", MappingsJar.class, task -> {
            task.setGroup(TaskGroups.MAPPING_BUILD);
            task.getArchiveBaseName().set("obfToNamed" + variant);
            task.mappings(mappingTask.flatMap(GenerateObfToNamedTiny::getOutput));
        });
        project.getTasks().named("assemble", task -> task.dependsOn(jarTask));
    }
}
