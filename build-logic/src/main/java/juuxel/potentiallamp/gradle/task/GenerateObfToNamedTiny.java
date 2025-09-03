package juuxel.potentiallamp.gradle.task;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Creates a mapping file that drops the {@code intermediary} namespace
 * and fills it with {@code official} names and fills missing {@code named}
 * names with one of the other namespaces.
 */
public abstract class GenerateObfToNamedTiny extends DefaultTask {
    private static final Pattern INTERMEDIARY_PATTERN = Pattern.compile("^(.+/class|method|field)_[0-9]+$");

    @Input
    public abstract Property<String> getFallbackNamespace();

    @InputFile
    public abstract RegularFileProperty getIntermediary();

    @InputFile
    public abstract RegularFileProperty getMappings();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    protected void run() throws IOException {
        var mappingTree = new MemoryMappingTree();
        MappingReader.read(getIntermediary().get().getAsFile().toPath(), mappingTree);
        MappingReader.read(getMappings().get().getAsFile().toPath(), mappingTree);

        String fallbackNamespace = getFallbackNamespace().get();

        if (!"intermediary".equals(fallbackNamespace)) {
            // Delete all intermediary names
            for (var clazz : mappingTree.getClasses()) {
                removeIntermediary(clazz);
                for (var method : clazz.getMethods()) {
                    removeIntermediary(method);
                }
                for (var field : clazz.getFields()) {
                    removeIntermediary(field);
                }
            }
        }

        try (Writer writer = Files.newBufferedWriter(getOutput().get().getAsFile().toPath());
             Tiny2FileWriter tinyWriter = new Tiny2FileWriter(writer, false)) {
            // 4. Fill in new intermediary ns with obfuscated names for Loom's noIntermediateMappings() option
            var intermediaryCompleter = new MappingNsCompleter(tinyWriter, Map.of("intermediary", "official"));

            // 3. Add new empty intermediary ns
            var intermediaryAdder = new MappingDstNsReorder(intermediaryCompleter, "intermediary", "named");

            // 2. Remove real intermediary
            var intermediaryRemover = new MappingDstNsReorder(intermediaryAdder, "named");

            // 1. Fill in blanks from the fallback namespace
            var namedCompleter = new MappingNsCompleter(intermediaryRemover, Map.of("named", fallbackNamespace));
            mappingTree.accept(namedCompleter);
        }
    }

    private static void removeIntermediary(MappingTree.ElementMapping entry) {
        String name = entry.getDstName(1);
        if (name != null && INTERMEDIARY_PATTERN.matcher(name).matches()) {
            entry.setDstName(entry.getSrcName(), 1);
        }
    }
}
