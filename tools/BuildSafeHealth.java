import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class BuildSafeHealth implements Opcodes {
    static final String BASE = "io/github/adytech99/healthindicators/";
    static final String LIVING = "Lnet/minecraft/class_1309;";
    static final String STACK = "Lnet/minecraft/class_4587;";
    static final String CLIENT = "Lnet/minecraft/class_310;";
    static final String BUFFERS = "Lnet/minecraft/class_4597;";
    static final String EXTRA_MIXINS = "safehealth.mixins.json";

    static AbstractInsnNode first(MethodNode method, Predicate<AbstractInsnNode> match) {
        for (var node : method.instructions.toArray()) if (match.test(node)) return node;
        throw new IllegalArgumentException("Unsupported source bytecode in " + method.name);
    }

    static void remove(MethodNode method, AbstractInsnNode from, AbstractInsnNode until) {
        for (var node = from; node != until;) {
            var next = node.getNext();
            method.instructions.remove(node);
            node = next;
        }
    }

    // PlayerEntityRenderer already translates the supplied matrix for a below-name
    // objective when drawing the actual name. Remove the former second translation.
    static void removeScoreboardLift(MethodNode method) {
        var threshold = first(method, n -> n instanceof LdcInsnNode ldc && Double.valueOf(100).equals(ldc.cst));
        var end = ((JumpInsnNode) threshold.getNext().getNext()).label;
        var first = threshold.getPrevious();
        if (first.getOpcode() != DLOAD) throw new IllegalArgumentException("scoreboard distance");
        remove(method, first, end);
    }

    static void patchRenderer(ClassNode clazz) {
        clazz.interfaces.add("safehealth/HealthDraw");
        if (clazz.signature != null) clazz.signature += "Lsafehealth/HealthDraw;";
        var render = clazz.methods.stream().filter(m -> m.name.equals("render")).findFirst().orElseThrow();
        if (render.visibleAnnotations == null ||
            !render.visibleAnnotations.removeIf(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")))
            throw new IllegalArgumentException("Missing original render injection");
        render.name = "safehealth$draw";
        render.desc = "(" + LIVING + "FF" + STACK + BUFFERS + "I)V";
        render.signature = null;

        // Keep the original target eligibility rules ahead of the more expensive
        // visibility test. Replace the single old camera ray and headroom query.
        var distance = first(render, n -> n instanceof LdcInsnNode ldc && Double.valueOf(4096).equals(ldc.cst));
        var limit = (JumpInsnNode) distance.getNext().getNext();
        var cast = first(render, n -> n instanceof MethodInsnNode m && m.name.equals("method_17742"));
        var afterCast = cast;
        while (afterCast.getOpcode() != IF_ACMPNE) afterCast = afterCast.getNext();
        var headroomLabel = afterCast.getNext();
        if (limit.getOpcode() != IFGE || !(headroomLabel instanceof LabelNode))
            throw new IllegalArgumentException("Missing visibility boundaries");
        remove(render, limit.getNext(), headroomLabel);

        var query = first(render, n -> n instanceof MethodInsnNode m && m.name.equals("method_20812"));
        var end = query;
        while (end.getOpcode() != IFNE) end = end.getNext();
        var start = headroomLabel.getNext();
        while (start.getOpcode() < 0) start = start.getNext();
        var gate = new InsnList();
        gate.add(new VarInsnNode(ALOAD, 1));
        gate.add(new VarInsnNode(FLOAD, 3));
        gate.add(new MethodInsnNode(INVOKESTATIC, "net/minecraft/class_310", "method_1551", "()" + CLIENT, false));
        gate.add(new VarInsnNode(ALOAD, 4));
        gate.add(new MethodInsnNode(INVOKESTATIC, "safehealth/SafeVisibility", "visible",
            "(" + LIVING + "F" + CLIENT + STACK + ")Z", false));
        gate.add(new JumpInsnNode(IFEQ, limit.label));
        render.instructions.insertBefore(start, gate);
        remove(render, start, end.getNext());

        for (var method : clazz.methods) {
            if (method.name.equals("renderHearts") || method.name.equals("renderNumber"))
                removeScoreboardLift(method);
        }
    }

    static byte[] patch(String name, byte[] bytes) {
        if (name.equals("fabric.mod.json")) {
            String old = new String(bytes, StandardCharsets.UTF_8);
            String next = old.replace("\"version\": \"2.5.7\"", "\"version\": \"2.5.7+visibility.1\"")
                .replace("\"healthindicators.mixins.json\"", "\"healthindicators.mixins.json\", \"" + EXTRA_MIXINS + "\"")
                .replace("Displays a health bar above mobs and players", "Displays health above eligible visible players")
                .replace("\"MIT\"", "\"LicenseRef-HealthIndicators-Visibility\"");
            if (!next.contains(EXTRA_MIXINS) || !next.contains("2.5.7+visibility.1"))
                throw new IllegalArgumentException("Unexpected mod metadata");
            return next.getBytes(StandardCharsets.UTF_8);
        }
        if (!List.of(BASE + "mixin/NewEntityRendererMixin.class", BASE + "RenderTracker.class",
                BASE + "config/Config.class").contains(name)) return bytes;
        ClassNode clazz = new ClassNode();
        new ClassReader(bytes).accept(clazz, 0);
        switch (clazz.name) {
            case BASE + "RenderTracker" -> OptimizeTracker.patch(clazz);
            case BASE + "config/Config" -> OptimizeTracker.patchConfig(clazz);
            default -> patchRenderer(clazz);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        clazz.accept(writer);
        return writer.toByteArray();
    }

    static void put(ZipOutputStream output, String name, byte[] data) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(data);
        output.closeEntry();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) throw new IllegalArgumentException("Expected original.jar, classes directory, output.jar, visibility license");
        Path classes = Path.of(args[1]);
        String mixins = """
            {"required":true,"minVersion":"0.8","package":"safehealth.mixin","compatibilityLevel":"JAVA_17",
             "client":["LabelMixin","ClientWorldLookup"],"injectors":{"defaultRequire":1}}
            """;
        try (var input = new ZipFile(args[0]); var out = new ZipOutputStream(Files.newOutputStream(Path.of(args[2])))) {
            for (var entry : Collections.list(input.entries())) {
                try (var in = input.getInputStream(entry)) {
                    put(out, entry.getName(), patch(entry.getName(), in.readAllBytes()));
                }
            }
            put(out, EXTRA_MIXINS, mixins.getBytes(StandardCharsets.UTF_8));
            put(out, "LICENSE_Visibility", Files.readAllBytes(Path.of(args[3])));
            try (var files = Files.walk(classes.resolve("safehealth"))) {
                for (var path : files.filter(p -> p.toString().endsWith(".class")).sorted().toList())
                    put(out, classes.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
    }
}
