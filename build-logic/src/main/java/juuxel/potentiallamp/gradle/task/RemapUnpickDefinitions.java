package juuxel.potentiallamp.gradle.task;

import daomephsta.unpick.api.ValidatingUnpickV3Visitor;
import daomephsta.unpick.api.classresolvers.ClassResolvers;
import daomephsta.unpick.api.classresolvers.IClassResolver;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Reader;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Remapper;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Writer;
import daomephsta.unpick.constantmappers.datadriven.tree.UnpickV3Visitor;
import juuxel.potentiallamp.gradle.util.ClassIndex;
import juuxel.potentiallamp.gradle.util.Dotty;
import juuxel.potentiallamp.gradle.util.PathUtil;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;

public abstract class RemapUnpickDefinitions extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getInputFile();

    @InputFile
    public abstract RegularFileProperty getMappings();

    @Input
    public abstract Property<String> getSourceNamespace();

    @Input
    public abstract Property<String> getTargetNamespace();

    @InputFile
    public abstract RegularFileProperty getGameJar();

    @InputDirectory
    public abstract DirectoryProperty getConstantClasses();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    public RemapUnpickDefinitions() {
        getSourceNamespace().convention("named");
        getTargetNamespace().convention("official");
    }

    @TaskAction
    protected void remap() throws IOException {
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(PathUtil.get(getMappings()), tree);
        int srcNsIndex = tree.getNamespaceId(getSourceNamespace().get());
        int dstNsIndex = tree.getNamespaceId(getTargetNamespace().get());
        UnpickV3Writer writer = new UnpickV3Writer();
        Path gameJarPath = PathUtil.get(getGameJar());
        ClassIndex index = ClassIndex.create(gameJarPath);

        try (Reader reader = Files.newBufferedReader(PathUtil.get(getInputFile()));
             UnpickV3Reader unpickReader = new UnpickV3Reader(reader);
             JarFile gameJar = new JarFile(gameJarPath.toFile())) {
            IClassResolver classResolver = ClassResolvers.jar(gameJar).chain(ClassResolvers.fromDirectory(PathUtil.get(getConstantClasses())));
            unpickReader.accept(new Validator(new RemapperImpl(writer, tree, srcNsIndex, dstNsIndex, index), classResolver, index));
        }

        Files.writeString(PathUtil.get(getOutputFile()), writer.getOutput(), StandardCharsets.UTF_8);
    }

    private static final class Validator extends ValidatingUnpickV3Visitor {
        private final ClassIndex index;

        Validator(UnpickV3Visitor downstream, IClassResolver classResolver, ClassIndex index) {
            super(classResolver, downstream);
            this.index = index;
        }

        @Override
        public boolean packageExists(@Dotty String packageName) {
            return index.packageExists(packageName);
        }
    }

    private static final class RemapperImpl extends UnpickV3Remapper {
        private final MappingTreeView mappings;
        private final int srcNsIndex;
        private final int dstNsIndex;
        private final ClassIndex index;

        RemapperImpl(UnpickV3Visitor downstream, MappingTreeView mappings, int srcNsIndex, int dstNsIndex, ClassIndex index) {
            super(downstream);
            this.mappings = mappings;
            this.srcNsIndex = srcNsIndex;
            this.dstNsIndex = dstNsIndex;
            this.index = index;
        }

        @Override
        protected @Dotty String mapClassName(@Dotty String className) {
            return mappings.mapClassName(className.replace('.', '/'), srcNsIndex, dstNsIndex).replace('/', '.');
        }

        @Override
        protected String mapFieldName(@Dotty String className, String fieldName, String fieldDesc) {
            MappingTreeView.ClassMappingView c = mappings.getClass(className.replace('.', '/'), srcNsIndex);
            if (c == null) return fieldName;
            MappingTreeView.FieldMappingView f = c.getField(fieldName, fieldDesc, srcNsIndex);
            if (f == null) return fieldName;
            return Objects.requireNonNullElse(f.getName(dstNsIndex), fieldName);
        }

        @Override
        protected String mapMethodName(@Dotty String className, String methodName, String methodDesc) {
            MappingTreeView.ClassMappingView c = mappings.getClass(className.replace('.', '/'), srcNsIndex);
            if (c == null) return methodName;
            MappingTreeView.MethodMappingView m = c.getMethod(methodName, methodDesc, srcNsIndex);
            if (m == null) return methodName;
            return Objects.requireNonNullElse(m.getName(dstNsIndex), methodName);
        }

        @Override
        protected List<@Dotty String> getClassesInPackage(@Dotty String pkg) {
            return index.getClassesInPackage(pkg);
        }

        @Override
        protected String getFieldDesc(@Dotty String className, String fieldName) {
            return index.getFieldDesc(className, fieldName).orElseThrow();
        }
    }
}
