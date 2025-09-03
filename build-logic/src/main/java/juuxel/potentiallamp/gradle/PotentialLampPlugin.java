package juuxel.potentiallamp.gradle;

import juuxel.potentiallamp.gradle.task.CompleteNames;
import juuxel.potentiallamp.gradle.task.CreateUnpickConfig;
import juuxel.potentiallamp.gradle.task.DownloadGameJar;
import juuxel.potentiallamp.gradle.task.DownloadVersionManifest;
import juuxel.potentiallamp.gradle.task.GenerateObfToNamedTiny;
import juuxel.potentiallamp.gradle.task.LaunchEnigma;
import juuxel.potentiallamp.gradle.task.MapJar;
import juuxel.potentiallamp.gradle.task.MapSpecializedMethods;
import juuxel.potentiallamp.gradle.task.MappingsJar;
import juuxel.potentiallamp.gradle.task.MergeUnpickDefinitions;
import juuxel.potentiallamp.gradle.task.RemapUnpickDefinitions;
import juuxel.potentiallamp.gradle.task.UnpickJar;
import juuxel.potentiallamp.gradle.util.TaskGroups;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

public final class PotentialLampPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(BasePlugin.class);
        project.getPlugins().apply(JavaBasePlugin.class);

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
        var mapIntermediaryJar = registerMapJar(
            "mapIntermediaryJar",
            project,
            extension,
            downloadGameJar.flatMap(DownloadGameJar::getGameJar),
            extension.getIntermediaryFile(),
            "intermediary"
        );
        var mapSpecializedMethods = project.getTasks().register("mapSpecializedMethods", MapSpecializedMethods.class, task -> {
            task.setGroup(TaskGroups.MAPPING_BUILD);
            task.getInputMappings().set(extension.getMappingsDir());
            task.getGameJar().set(mapIntermediaryJar.flatMap(MapJar::getOutputJar));
            task.getOutputFile().set(extension.getCacheFile("built_mappings.tiny"));
        });
        var completeNames = project.getTasks().register("completeNames", CompleteNames.class, task -> {
            task.setGroup(TaskGroups.MAPPING_BUILD);
            task.getInputFile().set(mapSpecializedMethods.flatMap(MapSpecializedMethods::getOutputFile));
            task.getIntermediaryFile().set(extension.getIntermediaryFile());
            task.getIntermediaryGameJar().set(mapIntermediaryJar.flatMap(MapJar::getOutputJar));
            task.getOutputFile().set(extension.getCacheFile("completed_mappings.tiny"));
        });
        var mapNamedJar = registerMapJar(
            "mapNamedJar",
            project,
            extension,
            downloadGameJar.flatMap(DownloadGameJar::getGameJar),
            completeNames.flatMap(CompleteNames::getOutputFile),
            "named"
        );

        SourceSet constantsSourceSet = setupConstants(project);
        var createUnpickConfig = project.getTasks().register("createUnpickConfig", CreateUnpickConfig.class, task -> {
            task.setGroup(TaskGroups.MAPPING_BUILD);
            task.getConstants().set(project.provider(() -> "io.github.antiquitymc:potential-lamp:" + project.getVersion() + ":constants"));
            task.getOutputFile().set(extension.getCacheFile("unpick.json"));
        });
        var mergeUnpickDefinitions = project.getTasks().register("mergeUnpickDefinitions", MergeUnpickDefinitions.class, task -> {
            task.setGroup(TaskGroups.MAPPING_BUILD);
            task.getDefinitions().from(project.fileTree(extension.getUnpickDefinitionsDir()));
            task.getOutputFile().set(extension.getCacheFile("merged.unpick"));
        });
        var remapUnpickDefinitionsOfficial = registerRemapUnpick(
            project,
            extension,
            "Official",
            "official",
            mergeUnpickDefinitions.flatMap(MergeUnpickDefinitions::getOutputFile),
            completeNames.flatMap(CompleteNames::getOutputFile),
            mapNamedJar.flatMap(MapJar::getOutputJar),
            constantsSourceSet.getJava().getClassesDirectory()
        );
        var remapUnpickDefinitionsIntermediary = registerRemapUnpick(
            project,
            extension,
            "Intermediary",
            "intermediary",
            mergeUnpickDefinitions.flatMap(MergeUnpickDefinitions::getOutputFile),
            completeNames.flatMap(CompleteNames::getOutputFile),
            mapNamedJar.flatMap(MapJar::getOutputJar),
            constantsSourceSet.getJava().getClassesDirectory()
        );
        var unpickJar = project.getTasks().register("unpickIntermediaryJar", UnpickJar.class, task -> {
            task.getInputJar().set(mapIntermediaryJar.flatMap(MapJar::getOutputJar));
            task.getOutputJar().set(extension.getSuffixedGameJar("intermediary-unpicked"));
            task.getUnpickDefinitions().set(remapUnpickDefinitionsIntermediary.flatMap(RemapUnpickDefinitions::getOutputFile));
            task.getConstantClasses().set(constantsSourceSet.getJava().getClassesDirectory());
        });

        var unpickConfig = createUnpickConfig.flatMap(CreateUnpickConfig::getOutputFile);
        var unpickDefs = remapUnpickDefinitionsOfficial.flatMap(RemapUnpickDefinitions::getOutputFile);

        registerObfToNamedTiny(project, extension, completeNames.flatMap(CompleteNames::getOutputFile), unpickConfig, unpickDefs, "", "official");
        registerObfToNamedTiny(project, extension, completeNames.flatMap(CompleteNames::getOutputFile), unpickConfig, unpickDefs, "I", "intermediary");

        project.getRepositories().mavenCentral();
        project.getRepositories().maven(repo -> repo.setUrl("https://maven.fabricmc.net"));

        var enigmaRuntime = project.getConfigurations().create("enigmaRuntime");
        enigmaRuntime.setCanBeConsumed(false);
        project.getDependencies().addProvider("enigmaRuntime", extension.getEnigmaVersion().map(ver -> {
            var dep = project.getDependencies().create("cuchaz:enigma-swing:" + ver);

            if (dep instanceof ModuleDependency moduleDependency) {
                moduleDependency.attributes(attributes -> {
                    attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.SHADOWED));
                });
            }

            return dep;
        }));

        project.getTasks().register("enigma", LaunchEnigma.class, task -> {
            task.getClasspath().from(enigmaRuntime, extension.getFilamentJar());
            task.getGameJar().set(mapIntermediaryJar.flatMap(MapJar::getOutputJar));
            task.getMappingsDir().set(extension.getMappingsDir());
            task.getProfile().set(extension.getEnigmaProfile());
        });
        project.getTasks().register("enigmaUnpicked", LaunchEnigma.class, task -> {
            task.getClasspath().from(enigmaRuntime, extension.getFilamentJar());
            task.getGameJar().set(unpickJar.flatMap(UnpickJar::getOutputJar));
            task.getMappingsDir().set(extension.getMappingsDir());
            task.getProfile().set(extension.getEnigmaProfile());
        });
    }

    private static TaskProvider<MapJar> registerMapJar(String name, Project project, PotentialLampExtension extension, Provider<RegularFile> obfJar, Provider<RegularFile> mappings, String targetNamespace) {
        return project.getTasks().register(name, MapJar.class, task -> {
            task.setGroup(TaskGroups.JAR_SETUP);
            task.getInputJar().set(obfJar);
            task.getOutputJar().set(extension.getSuffixedGameJar(targetNamespace));
            task.getSourceNamespace().set("official");
            task.getTargetNamespace().set(targetNamespace);
            task.getMappings().set(mappings);
        });
    }

    private static void registerObfToNamedTiny(Project project, PotentialLampExtension extension, Provider<RegularFile> mappings, Provider<RegularFile> unpickConfig, Provider<RegularFile> unpickDefs, String variant, String fallbackNamespace) {
        var mappingTask = project.getTasks().register("generateObfToNamed" + variant + "Tiny", GenerateObfToNamedTiny.class, task -> {
            task.setGroup(TaskGroups.MAPPING_BUILD);
            task.getFallbackNamespace().set(fallbackNamespace);
            task.getIntermediary().set(extension.getIntermediaryFile());
            task.getMappings().set(mappings);
            task.getOutput().set(project.getLayout().getBuildDirectory().file("obfToNamed" + variant + ".tiny"));
        });
        var jarTask = project.getTasks().register("obfToNamed" + variant + "TinyJar", MappingsJar.class, task -> {
            task.setGroup(TaskGroups.MAPPING_BUILD);
            task.getArchiveFileName().set("obfToNamed" + variant + ".jar");
            task.mappings(mappingTask.flatMap(GenerateObfToNamedTiny::getOutput));

            task.from(unpickConfig, spec -> {
                spec.into("extras");
                spec.rename(unused -> "unpick.json");
            });
            task.from(unpickDefs, spec -> {
                spec.into("extras");
                spec.rename(unused -> "definitions.unpick");
            });
        });
        project.getTasks().named(BasePlugin.ASSEMBLE_TASK_NAME, task -> task.dependsOn(jarTask));
    }

    private static SourceSet setupConstants(Project project) {
        SourceSet sourceSet = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().create("constants");
        var jar = project.getTasks().register("constantsJar", Jar.class, task -> {
            task.setGroup(TaskGroups.MAPPING_BUILD);
            task.from(sourceSet.getOutput());
            task.getArchiveFileName().set("constants.jar");
        });
        project.getTasks().named(BasePlugin.ASSEMBLE_TASK_NAME, task -> task.dependsOn(jar));
        return sourceSet;
    }

    private static TaskProvider<RemapUnpickDefinitions> registerRemapUnpick(Project project, PotentialLampExtension extension, String suffix, String namespace, Provider<RegularFile> inputFile, Provider<RegularFile> mappings, Provider<RegularFile> gameJar, Provider<Directory> constantClasses) {
        return project.getTasks().register("remapUnpickDefinitions" + suffix, RemapUnpickDefinitions.class, task -> {
            task.setGroup(TaskGroups.MAPPING_BUILD);
            task.getInputFile().set(inputFile);
            task.getOutputFile().set(extension.getCacheFile("remapped-" + namespace + ".unpick"));
            task.getTargetNamespace().set(namespace);
            task.getMappings().set(mappings);
            task.getGameJar().set(gameJar);
            task.getConstantClasses().set(constantClasses);
        });
    }
}
