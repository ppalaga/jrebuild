package org.l2x6.jrebuild.core.diff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.l2x6.jrebuild.core.build.ResourceMatch;

import java.lang.classfile.AccessFlags;
import java.lang.classfile.Attribute;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.reflect.AccessFlag;

/**
 * A utility for diffing two ClassModel instances.
 */
public record ClassFileDiff(ResourceMatch resourceMatch, String diff) {

    public static ClassFileDiff of(ClassModel left, ClassModel right) {
        DiffBuilder out = new DiffBuilder();

        compareHeader(left, right, out);
        compareInterfaces(left, right, out);
        compareClassAttributes(left, right, out);
        compareFields(left, right, out);
        compareMethods(left, right, out);

        return new ClassFileDiff(out.match, out.toString());
    }

    public static String diff(byte[] leftBytes, byte[] rightBytes) {
        ClassFile cf = ClassFile.of();
        return diff(cf.parse(leftBytes), cf.parse(rightBytes));
    }

    public static String diff(Path leftClassFile, Path rightClassFile) throws IOException {
        return diff(Files.readAllBytes(leftClassFile), Files.readAllBytes(rightClassFile));
    }

    private static void compareHeader(ClassModel left, ClassModel right, DiffBuilder out) {
        if (left.majorVersion() != right.majorVersion()
                || left.minorVersion() != right.minorVersion()) {
            out.add("Class version changed: %d.%d -> %d.%d"
                    .formatted(left.majorVersion(), left.minorVersion(),
                               right.majorVersion(), right.minorVersion()));
        }

        String leftFlags = renderFlags(left.flags());
        String rightFlags = renderFlags(right.flags());
        if (!leftFlags.equals(rightFlags)) {
            out.add("Class flags changed: %s -> %s".formatted(leftFlags, rightFlags));
        }

        String leftThis = className(left.thisClass());
        String rightThis = className(right.thisClass());
        if (!leftThis.equals(rightThis)) {
            out.add("Class name changed: %s -> %s".formatted(leftThis, rightThis));
        }

        String leftSuper = left.superclass().map(ClassFileDiff::className).orElse("<none>");
        String rightSuper = right.superclass().map(ClassFileDiff::className).orElse("<none>");
        if (!leftSuper.equals(rightSuper)) {
            out.add("Superclass changed: %s -> %s".formatted(leftSuper, rightSuper));
        }
    }

    private static void compareInterfaces(ClassModel left, ClassModel right, DiffBuilder out) {
        Set<String> leftIfaces = left.interfaces().stream()
                .map(ClassFileDiff::className)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> rightIfaces = right.interfaces().stream()
                .map(ClassFileDiff::className)
                .collect(Collectors.toCollection(TreeSet::new));

        for (String iface : difference(leftIfaces, rightIfaces)) {
            out.add("Removed interface: %s".formatted(iface));
        }
        for (String iface : difference(rightIfaces, leftIfaces)) {
            out.add("Added interface: %s".formatted(iface));
        }
    }

    private static void compareClassAttributes(ClassModel left, ClassModel right, DiffBuilder out) {
        compareAttributeNames("Class", left.attributes(), right.attributes(), out);
    }

    private static void compareFields(ClassModel left, ClassModel right, DiffBuilder out) {
        Map<String, FieldModel> leftFields = indexFields(left.fields());
        Map<String, FieldModel> rightFields = indexFields(right.fields());

        for (String key : difference(leftFields.keySet(), rightFields.keySet())) {
            out.add("Removed field: %s".formatted(renderField(leftFields.get(key))));
        }
        for (String key : difference(rightFields.keySet(), leftFields.keySet())) {
            out.add("Added field: %s".formatted(renderField(rightFields.get(key))));
        }

        for (String key : intersection(leftFields.keySet(), rightFields.keySet())) {
            compareField(leftFields.get(key), rightFields.get(key), out);
        }
    }

    private static void compareField(FieldModel left, FieldModel right, DiffBuilder out) {
        String fieldLabel = fieldKey(left);

        String leftFlags = renderFlags(left.flags());
        String rightFlags = renderFlags(right.flags());
        if (!leftFlags.equals(rightFlags)) {
            out.add("Changed field: %s".formatted(fieldLabel));
            out.addIndented(builder -> builder.add("flags: %s -> %s".formatted(leftFlags, rightFlags)));
        }

        compareAttributeNames("Field " + fieldLabel, left.attributes(), right.attributes(), out);
    }

    private static void compareMethods(ClassModel left, ClassModel right, DiffBuilder out) {
        Map<String, MethodModel> leftMethods = indexMethods(left.methods());
        Map<String, MethodModel> rightMethods = indexMethods(right.methods());

        for (String key : difference(leftMethods.keySet(), rightMethods.keySet())) {
            out.add("Removed method: %s".formatted(renderMethod(leftMethods.get(key))));
        }
        for (String key : difference(rightMethods.keySet(), leftMethods.keySet())) {
            out.add("Added method: %s".formatted(renderMethod(rightMethods.get(key))));
        }

        for (String key : intersection(leftMethods.keySet(), rightMethods.keySet())) {
            compareMethod(leftMethods.get(key), rightMethods.get(key), out);
        }
    }

    static class PrependedAdd {

        private String prefix;
        private final DiffBuilder out;

        public PrependedAdd(String prefix, DiffBuilder out) {
            super();
            this.prefix = prefix;
            this.out = out;
        }

        public void add(String line) {
            if (prefix != null) {
                out.add(prefix);
                prefix = null;
            }
            out.add(line);
        }

    }

    private static void compareMethod(MethodModel left, MethodModel right, DiffBuilder diffBuilder) {

        String methodLabel = methodKey(left);
        PrependedAdd section = new PrependedAdd("Changed method: %s".formatted(methodLabel), diffBuilder);
        String leftFlags = renderFlags(left.flags());
        String rightFlags = renderFlags(right.flags());
        if (!leftFlags.equals(rightFlags)) {
            section.add("flags: %s -> %s".formatted(leftFlags, rightFlags));
        }

        AttributeNameDiff attrDiff = attributeNameDiff(left.attributes(), right.attributes());
        if (!attrDiff.removed.isEmpty() || !attrDiff.added.isEmpty()) {
            for (String name : attrDiff.removed) {
                section.add("removed attribute: %s".formatted(name));
            }
            for (String name : attrDiff.added) {
                section.add("added attribute: %s".formatted(name));
            }
        }

        Optional<CodeModel> leftCode = left.code();
        Optional<CodeModel> rightCode = right.code();

        if (leftCode.isEmpty() && rightCode.isPresent()) {
            section.add("code: <absent> -> <present>");
            section.add("new code:");
            diffBuilder.addIndented(builder -> builder.addLines(rightCode.get().toDebugString()));
        } else if (leftCode.isPresent() && rightCode.isEmpty()) {
            section.add("code: <present> -> <absent>");
        } else if (leftCode.isPresent()) {
            String leftBody = leftCode.get().toDebugString();
            String rightBody = rightCode.get().toDebugString();
            if (!leftBody.equals(rightBody)) {
                section.add("code:");
                appendSimpleUnifiedDiff(section, leftBody, rightBody);
            }
        }
    }

    private static void compareAttributeNames(
            String owner,
            List<Attribute<?>> leftAttrs,
            List<Attribute<?>> rightAttrs,
            DiffBuilder out
    ) {
        AttributeNameDiff diff = attributeNameDiff(leftAttrs, rightAttrs);
        if (diff.removed.isEmpty() && diff.added.isEmpty()) {
            return;
        }

        out.add("Changed %s attributes:".formatted(owner));
        out.addIndented(() -> {
            for (String name : diff.removed) {
                out.add("- %s".formatted(name));
            }
            for (String name : diff.added) {
                out.add("+ %s".formatted(name));
            }
        });
    }

    private static AttributeNameDiff attributeNameDiff(
            List<Attribute<?>> leftAttrs,
            List<Attribute<?>> rightAttrs
    ) {
        // Compare by multiset-ish grouped names, but rendered as repeated sorted names.
        List<String> left = attributeNames(leftAttrs);
        List<String> right = attributeNames(rightAttrs);

        List<String> removed = subtract(left, right);
        List<String> added = subtract(right, left);
        return new AttributeNameDiff(removed, added);
    }

    private static List<String> attributeNames(List<Attribute<?>> attrs) {
        return attrs.stream()
                .map(a -> a.attributeName().stringValue())
                .sorted()
                .toList();
    }

    private static List<String> subtract(List<String> a, List<String> b) {
        List<String> remaining = new ArrayList<>(b);
        List<String> result = new ArrayList<>();
        for (String s : a) {
            if (!remaining.remove(s)) {
                result.add(s);
            }
        }
        return result;
    }

    private static Map<String, FieldModel> indexFields(List<FieldModel> fields) {
        return fields.stream()
                .sorted(Comparator.comparing(ClassFileDiff::fieldKey))
                .collect(Collectors.toMap(
                        ClassFileDiff::fieldKey,
                        f -> f,
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
    }

    private static Map<String, MethodModel> indexMethods(List<MethodModel> methods) {
        return methods.stream()
                .sorted(Comparator.comparing(ClassFileDiff::methodKey))
                .collect(Collectors.toMap(
                        ClassFileDiff::methodKey,
                        m -> m,
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
    }

    private static String fieldKey(FieldModel field) {
        return field.fieldName().stringValue() + ":" + field.fieldType().stringValue();
    }

    private static String methodKey(MethodModel method) {
        return method.methodName().stringValue() + method.methodType().stringValue();
    }

    private static String renderField(FieldModel field) {
        return "%s %s %s".formatted(
                renderFlags(field.flags()),
                field.fieldType().stringValue(),
                field.fieldName().stringValue()
        ).trim().replaceAll("\\s+", " ");
    }

    private static String renderMethod(MethodModel method) {
        return "%s %s%s".formatted(
                renderFlags(method.flags()),
                method.methodName().stringValue(),
                method.methodType().stringValue()
        ).trim().replaceAll("\\s+", " ");
    }

    private static String renderFlags(AccessFlags flags) {
        if (flags == null) {
            return "<none>";
        }
        Set<AccessFlag> set = flags.flags();
        if (set.isEmpty()) {
            return "<none>";
        }
        return set.stream()
                .map(f -> f.name().toLowerCase())
                .sorted()
                .collect(Collectors.joining(" "));
    }

    private static String className(ClassEntry entry) {
        // internalName() returns slash-separated JVM names; convert to a friendlier form.
        return entry.asInternalName().replace('/', '.');
    }

    private static <T> Set<T> difference(Collection<T> a, Collection<T> b) {
        Set<T> result = new LinkedHashSet<>(a);
        result.removeAll(b);
        return result;
    }

    private static <T> Set<T> intersection(Collection<T> a, Collection<T> b) {
        Set<T> result = new LinkedHashSet<>(a);
        result.retainAll(b);
        return result;
    }

    private static void appendBlock(PrependedAdd section, int extraIndent, String block) {
        for (String line : block.split("\n", -1)) {
            section.addWithIndent(extraIndent, line);
        }
    }

    /**
     * A small line-oriented diff. Not minimal, but readable.
     */
    private static void appendSimpleUnifiedDiff(DiffBuilder out, String left, String right) {
        List<String> a = List.of(left.split("[\r\n]+"));
        List<String> b = List.of(right.split("[\r\n]+"));

        int prefix = 0;
        while (prefix < a.size() && prefix < b.size() && a.get(prefix).equals(b.get(prefix))) {
            prefix++;
        }

        int suffix = 0;
        while (suffix < (a.size() - prefix)
                && suffix < (b.size() - prefix)
                && a.get(a.size() - 1 - suffix).equals(b.get(b.size() - 1 - suffix))) {
            suffix++;
        }

        if (prefix > 0) {
            out.addWithIndent(1, "  ...");
        }

        for (int i = prefix; i < a.size() - suffix; i++) {
            out.addWithIndent(1, "- " + a.get(i));
        }
        for (int i = prefix; i < b.size() - suffix; i++) {
            out.addWithIndent(1, "+ " + b.get(i));
        }

        if (suffix > 0) {
            out.addWithIndent(1, "  ...");
        }
    }

    private record AttributeNameDiff(List<String> removed, List<String> added) {
    }

    private static final class DiffBuilder {
        private final StringBuilder sb = new StringBuilder();
        private int indentLevel;
        private ResourceMatch match = ResourceMatch.SUFFICIENT; // if we do this kind of diffing, the binaries are different and thus the result cannot be PERFECT

        DiffBuilder() {
            this.indentLevel = 0;
        }

        void add(String line) {
            addWithIndent(0, line);
        }

        void addWithIndent(int extraIndent, String line) {
            match = ResourceMatch.MISMATCH;
            sb.append("  ".repeat(Math.max(0, indentLevel + extraIndent)))
              .append(line)
              .append('\n');
        }

        void addIndented(Consumer<DiffBuilder> action) {
            indentLevel++;
            try {
                action.accept(this);
            } finally {
                indentLevel--;
            }
        }

        void addLines(String lines) {
            final StringTokenizer st = new StringTokenizer(lines, "\r\n");
            while (st.hasMoreTokens()) {
                add(st.nextToken());
            }
        }

//        void addAll(DiffBuilder other) {
//            for (String line : other.toString().split("\n")) {
//                if (!line.isEmpty()) {
//                    addWithIndent(0, line);
//                }
//            }
//        }

        @Override
        public String toString() {
            return sb.toString().stripTrailing();
        }
    }

    // Example CLI:
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java ClassModelDiff <left.class> <right.class>");
            System.exit(2);
        }

        String result = diff(Path.of(args[0]), Path.of(args[1]));
        System.out.println(result);
    }
}