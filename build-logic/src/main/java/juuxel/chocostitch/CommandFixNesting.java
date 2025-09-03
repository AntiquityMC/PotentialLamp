package juuxel.chocostitch;

import net.fabricmc.stitch.Command;
import net.fabricmc.stitch.representation.JarClassEntry;
import net.fabricmc.stitch.representation.JarReader.Builder;
import net.fabricmc.stitch.representation.JarRootEntry;
import net.fabricmc.stitch.util.StitchUtil;
import net.fabricmc.stitch.util.StitchUtil.FileSystemDelegate;
import org.apache.commons.io.FilenameUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * @author <a href="https://github.com/Chocohead/Stitch/blob/old/src/main/java/net/fabricmc/stitch/commands/CommandFixNesting.java">Chocohead</a>,
 * licensed under Apache 2.0
 */
public class CommandFixNesting extends Command {
    public CommandFixNesting() {
        super("fixNesting");
    }

    @Override
    public String getHelpString() {
        return "<jar>";
    }

    @Override
    public boolean isArgumentCountValid(int count) {
        return count == 1;
    }

    @Override
    public void run(String[] args) throws Exception {
        run(new File(args[0]));
    }

    public static void run(File jar) throws IOException {
        JarRootEntry jarEntry = new JarRootEntry(jar);
        Builder.create(jarEntry).joinMethodEntries(false).build().apply();

        long nests = jarEntry.getClasses().stream().filter(entry -> !entry.getInnerClasses().isEmpty()).count();
        if (nests > 0) {
            System.out.println("Found " + nests + " nested classes to check");

            File oldJar = new File(jar.getParentFile(), jar.getName() + ".check");
            jar.renameTo(oldJar);

            try (JarFile oldJarFile = new JarFile(oldJar); FileSystemDelegate newFS = StitchUtil.getJarFileSystem(jar, true)) {
                List<JarClassEntry> missingInners = new ArrayList<>();
                String missingOuter = null;

                for (Enumeration<JarEntry> it = oldJarFile.entries(); it.hasMoreElements();) {
                    JarEntry entry = it.nextElement();
                    if (entry.isDirectory()) continue; //No need to copy these over

                    Path outPath = newFS.get().getPath(entry.getName());
                    if (outPath.getParent() != null && Files.notExists(outPath.getParent())) {
                        Files.createDirectories(outPath.getParent());
                    }

                    if (entry.getName().endsWith(".class")) {
                        JarClassEntry clazz = jarEntry.getClass(FilenameUtils.removeExtension(entry.getName()), false);
                        assert clazz != null: "Unable to find class entry for " + entry.getName();

                        ClassReader reader = null;
                        ClassNode node = null;
                        assert missingInners.isEmpty();
                        assert missingOuter == null;

                        if (!clazz.getInnerClasses().isEmpty()) {
                            (reader = new ClassReader(oldJarFile.getInputStream(entry))).accept(node = new ClassNode(), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

                            Set<String> inners = node.innerClasses.stream().map(inner -> inner.name).collect(Collectors.toSet());

                            for (JarClassEntry inner : clazz.getInnerClasses()) {
                                if (!inners.contains(inner.getFullyQualifiedName())) {
                                    missingInners.add(inner);
                                }
                            }
                        }

                        if (clazz.getFullyQualifiedName().indexOf('$') > 0) {
                            if (node == null) {
                                (reader = new ClassReader(oldJarFile.getInputStream(entry))).accept(node = new ClassNode(), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                            }

                            if (node.innerClasses.stream().map(inner -> inner.name).noneMatch(clazz.getFullyQualifiedName()::equals)) {
                                missingInners.add(clazz);
                            }

                            if (clazz.isAnonymous()) {//Anonymous classes mark parent classes by the outerClass attribute rather than the innerClass
                                String outer = getParent(clazz.getFullyQualifiedName());
                                if (!outer.equals(node.outerClass)) {
                                    missingOuter = outer;
                                }
                            } //Classes in methods also do this, but they're more of a nuisance to detect without guessing from line numbers
                        }

                        if (!missingInners.isEmpty() || missingOuter != null) {
                            assert node != null; //Should have been read to get to this state
                            assert reader != null; //So should this

                            //Create the writer with the fully intact class rather than reading the node in (which is missing the method code/debug/frames)
                            ClassWriter writer = new ClassWriter(reader, 0);
                            reader.accept(writer, 0);

                            if (!missingInners.isEmpty()) {
                                System.out.println("Fixing missing inners in " + clazz.getFullyQualifiedName() + ": " + missingInners);

                                for (JarClassEntry inner : missingInners) {
                                    writer.visitInnerClass(inner.getFullyQualifiedName(), inner.isAnonymous() ? null : getParent(inner.getFullyQualifiedName()), inner.isAnonymous() ? null : inner.getName(), inner.getAccess());
                                }

                                missingInners.clear();
                            }

                            if (missingOuter != null) {
                                System.out.println("Fixing missing outer for " + clazz.getName() + ": " + missingOuter);
                                writer.visitOuterClass(missingOuter, null, null); //Probably won't have to deal with method local classes
                                missingOuter = null;
                            }

                            Files.write(outPath, writer.toByteArray(), StandardOpenOption.CREATE_NEW);
                            continue;
                        }
                    }

                    Files.copy(oldJarFile.getInputStream(entry), outPath);
                }
            }

            oldJar.delete();
        } else {
            System.out.println("Found no nested classes in input");
        }
    }

    private static String getParent(String name) {
        int split = name.lastIndexOf('$');
        return split <= 0 ? null : name.substring(0, split);
    }
}
