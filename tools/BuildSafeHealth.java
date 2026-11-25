import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
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
    static final String ICONS = "assets/healthindicators/textures/gui/icons.png";
    static final String MOUNT_ICONS = "assets/healthindicators/textures/gui/mount_icons.png";
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

        patchPlacement(clazz, "renderHearts", "safehealth$drawHearts");
        patchPlacement(clazz, "renderNumber", "safehealth$drawNumbers");
    }

    private static void patchPlacement(ClassNode type, String renderer, String bridge) {
        var original = type.methods.stream().filter(m -> m.name.equals(renderer)).findFirst().orElseThrow();
        removeScoreboardLift(original);
        var hasLabel = first(original, n -> n instanceof MethodInsnNode m && m.name.equals("method_3921"));
        var skipLift = ((JumpInsnNode)hasLabel.getNext()).label;
        var placement = new InsnList();
        placement.add(new VarInsnNode(ALOAD, 1));
        placement.add(new MethodInsnNode(INVOKESTATIC, "safehealth/IndicatorPlacement", "isMount",
                "(Lnet/minecraft/class_1297;)Z", false));
        placement.add(new JumpInsnNode(IFNE, skipLift));
        original.instructions.insertBefore(hasLabel.getPrevious().getPrevious(), placement);

        var colors = new InsnList();
        colors.add(new VarInsnNode(ALOAD, 1));
        if (renderer.equals("renderHearts")) {
            var icons = first(original, n -> n instanceof FieldInsnNode f && f.name.equals("ICONS"));
            colors.add(new MethodInsnNode(INVOKESTATIC, "safehealth/IndicatorPlacement", "heartTexture",
                    "(Lnet/minecraft/class_2960;" + LIVING + ")Lnet/minecraft/class_2960;", false));
            original.instructions.insert(icons, colors);
        } else {
            var configuredColor = first(original, n -> n instanceof FieldInsnNode f && f.name.equals("number_color"));
            if (!(configuredColor.getNext() instanceof MethodInsnNode color) || !color.name.equals("getRGB"))
                throw new IllegalArgumentException("Missing number color call");
            colors.add(new MethodInsnNode(INVOKESTATIC, "safehealth/IndicatorPlacement", "numberColor",
                    "(I" + LIVING + ")I", false));
            original.instructions.insert(color, colors);
        }

        var lift = first(original, n -> n instanceof LdcInsnNode ldc && Double.valueOf(20.0).equals(ldc.cst));
        original.instructions.set(lift, new FieldInsnNode(GETSTATIC, "safehealth/IndicatorPlacement", "LIFT", "D"));

        // The mount hook shares the existing heart and number renderer without sharing player filters.
        var draw = new MethodNode(ACC_PUBLIC, bridge, original.desc, null, null);
        draw.instructions.add(new VarInsnNode(ALOAD, 0));
        draw.instructions.add(new VarInsnNode(ALOAD, 1));
        draw.instructions.add(new VarInsnNode(FLOAD, 2));
        draw.instructions.add(new VarInsnNode(FLOAD, 3));
        draw.instructions.add(new VarInsnNode(ALOAD, 4));
        draw.instructions.add(new VarInsnNode(ALOAD, 5));
        draw.instructions.add(new VarInsnNode(ILOAD, 6));
        draw.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, type.name, renderer, original.desc, false));
        draw.instructions.add(new InsnNode(RETURN));
        type.methods.add(draw);
    }

    private static void patchControls(ClassNode type) {
        var init = type.methods.stream().filter(m -> m.name.equals("onInitializeClient")).findFirst().orElseThrow();
        init.instructions.insertBefore(first(init, n -> n.getOpcode() == RETURN),
                new MethodInsnNode(INVOKESTATIC, "safehealth/ModeControls", "register", "()V", false));

        var tick = type.methods.stream().filter(m -> m.name.equals("lambda$onInitializeClient$0")).findFirst().orElseThrow();
        var setter = first(tick, n -> n instanceof MethodInsnNode m && m.name.equals("setRenderingEnabled"));
        var oldNotice = first(tick, n -> n instanceof MethodInsnNode m && m.name.equals("sendMessage"));
        if (oldNotice.getNext().getOpcode() != GOTO) throw new IllegalArgumentException("Unexpected toggle loop");
        var end = oldNotice.getNext();
        remove(tick, setter.getNext(), end);
        var notice = new InsnList();
        notice.add(new VarInsnNode(ALOAD, 1));
        notice.add(new MethodInsnNode(INVOKESTATIC, "safehealth/ModeControls", "showToggle", "(" + CLIENT + ")V", false));
        tick.instructions.insert(setter, notice);
    }

    private static byte[] addModeKeyLabel(byte[] bytes) {
        var old = new String(bytes, StandardCharsets.UTF_8).stripTrailing();
        if (!old.endsWith("}")) throw new IllegalArgumentException("Unexpected language JSON");
        return (old.substring(0, old.length() - 1).stripTrailing() +
                ",\n  \"key.healthindicators.switch_mount_mode\": \"Switch mount display mode\"\n}\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    static byte[] patch(String name, byte[] bytes) {
        if (name.equals("assets/healthindicators/lang/en_us.json")) return addModeKeyLabel(bytes);
        if (name.equals("fabric.mod.json")) {
            String old = new String(bytes, StandardCharsets.UTF_8);
            String next = old.replace("\"version\": \"2.5.7\"", "\"version\": \"2.5.7+visibility.6\"")
                .replace("\"healthindicators.mixins.json\"", "\"healthindicators.mixins.json\", \"" + EXTRA_MIXINS + "\"")
                .replace("Displays a health bar above mobs and players", "Displays health above eligible visible players, horses and camels")
                .replace("\"MIT\"", "\"LicenseRef-HealthIndicators-Visibility\"")
                .replace("\"name\": \"Health Indicators\"", "\"name\": \"체력바 모드\"");
            if (!next.contains(EXTRA_MIXINS) || !next.contains("2.5.7+visibility.6"))
                throw new IllegalArgumentException("Unexpected mod metadata");
            return next.getBytes(StandardCharsets.UTF_8);
        }
        if (!List.of(BASE + "mixin/NewEntityRendererMixin.class", BASE + "RenderTracker.class",
                BASE + "config/Config.class", BASE + "HealthIndicatorsMod.class").contains(name)) return bytes;
        ClassNode clazz = new ClassNode();
        new ClassReader(bytes).accept(clazz, name.equals(BASE + "HealthIndicatorsMod.class") ? ClassReader.EXPAND_FRAMES : 0);
        switch (clazz.name) {
            case BASE + "RenderTracker" -> OptimizeTracker.patch(clazz);
            case BASE + "config/Config" -> OptimizeTracker.patchConfig(clazz);
            case BASE + "HealthIndicatorsMod" -> patchControls(clazz);
            default -> patchRenderer(clazz);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        clazz.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] greenMountIcons(byte[] source) throws IOException {
        var original = ImageIO.read(new ByteArrayInputStream(source));
        if (original == null || original.getWidth() < 178 || original.getHeight() < 9)
            throw new IOException("Unexpected heart texture");
        var green = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
        float[] hsb = new float[3];
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int pixel = original.getRGB(x, y);
                boolean fillSprite = y < 9 && ((x >= 52 && x < 70) || (x >= 160 && x < 178));
                if (fillSprite && (pixel >>> 24) != 0) {
                    Color.RGBtoHSB((pixel >>> 16) & 255, (pixel >>> 8) & 255, pixel & 255, hsb);
                    if (hsb[1] > 0.12F)
                        pixel = (pixel & 0xFF000000) | (Color.HSBtoRGB(0.226F, 0.58F, hsb[2]) & 0xFFFFFF);
                }
                green.setRGB(x, y, pixel);
            }
        }
        var result = new ByteArrayOutputStream();
        if (!ImageIO.write(green, "PNG", result)) throw new IOException("Missing PNG encoder");
        return result.toByteArray();
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
             "client":["LabelMixin","MountMixin","ClientWorldLookup"],"injectors":{"defaultRequire":1}}
            """;
        try (var input = new ZipFile(args[0]); var out = new ZipOutputStream(Files.newOutputStream(Path.of(args[2])))) {
            for (var entry : Collections.list(input.entries())) {
                try (var in = input.getInputStream(entry)) {
                    byte[] bytes = in.readAllBytes();
                    put(out, entry.getName(), patch(entry.getName(), bytes));
                    if (entry.getName().equals(ICONS)) put(out, MOUNT_ICONS, greenMountIcons(bytes));
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
