package juuxel.potentiallamp.gradle.task;

import net.fabricmc.filament.nameproposal.MappingEntry;
import net.fabricmc.filament.nameproposal.NameFinder;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public abstract class CompleteNames extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getInputFile();

    @InputFile
    public abstract RegularFileProperty getIntermediaryGameJar();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    protected void run() throws IOException {
        Path inputPath = getInputFile().get().getAsFile().toPath();
        Path outputPath = getOutputFile().get().getAsFile().toPath();
        Path gameJar = getIntermediaryGameJar().get().getAsFile().toPath();

        var nameFinder = new NameFinder();
        loadJarIntoNameFinder(gameJar, nameFinder);
        getLogger().info("Found {} field names", nameFinder.getFieldNames().size());

        MemoryMappingTree tree = processMappings(inputPath, visitor -> new MappingNsCompleter(visitor, Map.of("named", "intermediary"), true));

        if (tree.visitHeader()) {
            tree.visitNamespaces("intermediary", List.of("named"));
        }

        for (MappingEntry fieldEntry : nameFinder.getFieldNames().keySet()) {
            if (tree.visitClass(fieldEntry.owner()) && tree.visitField(fieldEntry.name(), fieldEntry.desc())) {
                MappingTree.FieldMapping existingMapping = tree.getField(fieldEntry.owner(), fieldEntry.name(), fieldEntry.desc());
                assert existingMapping != null;
                String existingName = existingMapping.getName(0);

                if (existingName == null || existingName.startsWith("field_")) {
                    tree.visitDstName(MappedElementKind.FIELD, 0, nameFinder.getFieldNames().get(fieldEntry));
                }
            }
        }

        tree.visitEnd();

        try (MappingWriter writer = MappingWriter.create(outputPath, MappingFormat.TINY_2_FILE)) {
            tree.accept(writer);
        }
    }

    private static void loadJarIntoNameFinder(Path jarPath, NameFinder nameFinder) throws IOException {
        try (InputStream in = Files.newInputStream(jarPath); JarInputStream jar = new JarInputStream(in)) {
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    ClassNode cn = new ClassNode();
                    ClassReader cr = new ClassReader(jar);
                    cr.accept(cn, 0);
                    nameFinder.accept(cn);
                }
            }
        }
    }

    private static MemoryMappingTree processMappings(Path in, UnaryOperator<MappingVisitor> visitorOp) throws IOException {
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(in, visitorOp.apply(tree));
        return tree;
    }
}
