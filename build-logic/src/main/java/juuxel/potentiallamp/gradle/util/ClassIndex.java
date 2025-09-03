package juuxel.potentiallamp.gradle.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public final class ClassIndex {
    private final Map<@Slashy String, PackageEntry> packages;

    private ClassIndex(Map<@Slashy String, PackageEntry> packages) {
        this.packages = packages;
    }

    public List<@Dotty String> getClassesInPackage(@Dotty String packageName) {
        return packages.get(packageName.replace('.', '/'))
            .classesBySimpleName
            .values()
            .stream()
            .map(c -> packageName + '.' + c.simpleName)
            .toList();
    }

    public Optional<String> getFieldDesc(@Dotty String owner, String name) {
        @Dotty String[] nameParts = splitClassName(owner, '.');
        return Optional.ofNullable(packages.get(nameParts[0]))
            .flatMap(pkg -> Optional.ofNullable(pkg.classesBySimpleName.get(nameParts[1])))
            .stream()
            .flatMap(c -> c.fields.stream())
            .filter(f -> name.equals(f.name))
            .map(FieldEntry::desc)
            .findFirst();
    }

    public static ClassIndex create(Path jarPath) throws IOException {
        Map<@Slashy String, List<ClassEntry>> classesByPackage = new HashMap<>();
        try (InputStream in = Files.newInputStream(jarPath);
             JarInputStream jar = new JarInputStream(in)) {

            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;

                ClassNode cn = new ClassNode();
                ClassReader cr = new ClassReader(jar);
                cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                @Slashy String[] nameParts = splitClassName(cn.name, '/');
                ClassEntry classEntry = getClassEntry(nameParts[1], cn);
                classesByPackage.computeIfAbsent(nameParts[0], unused -> new ArrayList<>()).add(classEntry);
            }
        }

        Map<@Slashy String, PackageEntry> packages = new HashMap<>();
        classesByPackage.forEach((packageName, classes) -> {
            Map<String, ClassEntry> classesBySimpleName = new HashMap<>();
            for (ClassEntry classEntry : classes) {
                classesBySimpleName.put(classEntry.simpleName, classEntry);
            }
            packages.put(packageName, new PackageEntry(packageName, classesBySimpleName));
        });
        return new ClassIndex(packages);
    }

    private static String[] splitClassName(String name, char separator) {
        String[] result = new String[2];
        int separatorIndex = name.lastIndexOf(separator);
        if (separatorIndex >= 0) {
            result[0] = name.substring(0, separatorIndex);
            result[1] = name.substring(separatorIndex + 1);
        } else {
            result[0] = "";
            result[1] = name;
        }

        return result;
    }

    private static ClassEntry getClassEntry(String simpleName, ClassNode node) {
        List<FieldEntry> fields = new ArrayList<>();

        for (FieldNode field : node.fields) {
            FieldEntry entry = new FieldEntry(field.name, field.desc);
            fields.add(entry);
        }

        return new ClassEntry(simpleName, fields);
    }

    private record PackageEntry(@Slashy String name, Map<String, ClassEntry> classesBySimpleName) {
    }

    private record ClassEntry(String simpleName, List<FieldEntry> fields) {
    }

    private record FieldEntry(String name, String desc) {
    }
}
