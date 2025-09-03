package juuxel.potentiallamp.gradle;

import net.fabricmc.filament.nameproposal.NameFinder;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.io.File;

public abstract class PotentialLampExtension {
    @Inject
    public PotentialLampExtension(Project project) {
        getMappingsDir().convention(project.getLayout().getProjectDirectory().dir("mappings"));
        Provider<File> intermediaryFile = getMinecraftVersion().map(version -> project.file("intermediary/" + version + ".tiny"));
        getIntermediaryFile().convention(project.getLayout().file(intermediaryFile));
        getCacheDir().convention(project.getLayout().getBuildDirectory().dir("pl-cache"));
    }

    public abstract DirectoryProperty getMappingsDir();

    public abstract Property<String> getMinecraftVersion();

    public abstract RegularFileProperty getIntermediaryFile();

    public abstract DirectoryProperty getCacheDir();

    public Provider<RegularFile> getCacheFile(String name) {
        return getCacheDir().map(dir -> dir.file(name));
    }

    public Provider<RegularFile> getCacheFile(Provider<? extends CharSequence> name) {
        return getCacheDir().flatMap(dir -> dir.file(name));
    }

    public String getFilamentJar() {
        return NameFinder.class.getProtectionDomain().getCodeSource().getLocation().getFile();
    }
}
