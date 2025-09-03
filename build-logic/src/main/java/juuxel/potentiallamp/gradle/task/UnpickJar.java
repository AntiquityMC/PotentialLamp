package juuxel.potentiallamp.gradle.task;

import daomephsta.unpick.api.ConstantUninliner;
import daomephsta.unpick.api.classresolvers.ClassResolvers;
import daomephsta.unpick.api.classresolvers.IClassResolver;
import daomephsta.unpick.api.constantgroupers.ConstantGroupers;
import daomephsta.unpick.api.constantgroupers.IConstantGrouper;
import juuxel.potentiallamp.gradle.util.PathUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public abstract class UnpickJar extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getInputJar();

    @InputFile
    public abstract RegularFileProperty getUnpickDefinitions();

    @InputDirectory
    public abstract DirectoryProperty getConstantClasses();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    protected void unpick() throws IOException {
        try (OutputStream out = Files.newOutputStream(PathUtil.get(getOutputJar()));
             var jarOut = new JarOutputStream(out);
             var jarIn = new JarFile(getInputJar().get().getAsFile());
             var definitions = Files.newBufferedReader(PathUtil.get(getUnpickDefinitions()))) {
            IClassResolver classResolver = ClassResolvers.jar(jarIn).chain(ClassResolvers.fromDirectory(PathUtil.get(getConstantClasses())));
            IConstantGrouper grouper = ConstantGroupers.dataDriven()
                .classResolver(classResolver)
                .mappingSource(definitions)
                .build();

            ConstantUninliner uninliner = ConstantUninliner.builder()
                .classResolver(classResolver)
                .grouper(grouper)
                .build();

            Enumeration<JarEntry> entries = jarIn.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.isDirectory()) {
                    jarOut.putNextEntry(entry);
                    continue;
                } else if (!entry.getName().endsWith(".class")) {
                    try (InputStream fileIn = jarIn.getInputStream(entry)) {
                        jarOut.putNextEntry(entry);
                        fileIn.transferTo(jarOut);
                        continue;
                    }
                }

                try (InputStream classIn = jarIn.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(classIn);
                    ClassNode node = new ClassNode();
                    reader.accept(node, 0);
                    uninliner.transform(node);
                    ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                    byte[] data = writer.toByteArray();
                    jarOut.putNextEntry(entry);
                    jarOut.write(data);
                }
            }
        }
    }
}
