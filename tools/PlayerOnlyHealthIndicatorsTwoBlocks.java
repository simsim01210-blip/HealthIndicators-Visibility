
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.util.function.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.objectweb.asm.Opcodes.*;

class PlayerOnlyHealthIndicatorsTwoBlocks {
    static final String ROOT = "io/github/adytech99/healthindicators/";
    static final String CFG = ROOT + "config/ModConfig";
    static final Set<String> HEIGHT_FIELDS = Set.of("display_offset", "offset_step_size", "force_higher_offset_for_players", "passive_mobs", "hostile_mobs");
    static final Set<String> KEYS = Set.of("INCREASE_HEART_OFFSET_KEY_BINDING", "DECREASE_HEART_OFFSET_KEY_BINDING");
    static final String BOX = "net/minecraft/class_238";
    static final String ENTITY = "net/minecraft/class_1297";
    static final String PLAYER = "net/minecraft/class_1657";
    static final String WORLD = "net/minecraft/class_1937";
    static final String DISPATCHER = "net/minecraft/class_898";
    static final String CLIENT = "net/minecraft/class_310";
    static final String CAMERA = "net/minecraft/class_4184";
    static final String VECTOR = "net/minecraft/class_243";
    static final String RAY = "net/minecraft/class_3959";
    static final String HIT = "net/minecraft/class_3965";
    static final String HIT_TYPE = "net/minecraft/class_239$class_240";
    static final double HEIGHT = 20.0;
    static final double HEAD_CLEARANCE = 2.0;

    static String type(String name) { return "L" + name + ";"; }
    static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
    static MethodNode method(ClassNode clazz, String name) {
        return clazz.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    static AbstractInsnNode find(MethodNode method, Predicate<AbstractInsnNode> predicate) {
        return Arrays.stream(method.instructions.toArray()).filter(predicate).findFirst().orElseThrow();
    }
    static AbstractInsnNode previous(AbstractInsnNode node, int count) {
        for (int i = 0; i < count; i++) {
            do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        }
        return Objects.requireNonNull(node);
    }
    static void removeRange(MethodNode method, AbstractInsnNode start, AbstractInsnNode end) {
        for (var node = start; node != end;) {
            var next = node.getNext();
            method.instructions.remove(node);
            node = next;
        }
    }
    static boolean field(AbstractInsnNode node, String name) {
        return node instanceof FieldInsnNode value && value.name.equals(name);
    }
    static void removeAssignment(MethodNode method, String name, int inputs) {
        var assignment = find(method, node -> node.getOpcode() == PUTFIELD && field(node, name));
        removeRange(method, previous(assignment, inputs), assignment.getNext());
    }
    static void patchClient(ClassNode clazz) {
        var clinit = method(clazz, "<clinit>");
        for (String key : List.of("INCREASE_HEART_OFFSET_KEY_BINDING", "DECREASE_HEART_OFFSET_KEY_BINDING")) {
            var registration = find(clinit, node -> field(node, key));
            require(previous(registration, 7).getOpcode() == NEW, "key registration");
            removeRange(clinit, previous(registration, 7), registration.getNext());
        }
        var tick = method(clazz, "lambda$onInitializeClient$0");
        var adjust = find(tick, node -> field(node, "INCREASE_HEART_OFFSET_KEY_BINDING"));
        tick.instructions.insertBefore(adjust, new InsnNode(RETURN));
        removeRange(tick, adjust, null);

        var initialize = method(clazz, "onInitializeClient");
        var keyAutosave = find(initialize, node -> field(node, "START_CLIENT_TICK"));
        var registrationEnd = keyAutosave.getNext().getNext().getNext();
        require(registrationEnd instanceof MethodInsnNode call && call.name.equals("register"), "key autosave registration");
        removeRange(initialize, keyAutosave, registrationEnd.getNext());
        require(clazz.methods.removeIf(m -> m.name.equals("lambda$onInitializeClient$2")), "key autosave method");
        removeAssignment(method(clazz, "<init>"), "changed", 2);
        clazz.fields.removeIf(f -> KEYS.contains(f.name) || f.name.equals("changed"));
    }
    static void patchCommands(ClassNode clazz) {
        var register = method(clazz, "registerCommands");
        var offset = find(register, node -> node instanceof InvokeDynamicInsnNode indy
                && Arrays.stream(indy.bsmArgs).anyMatch(arg -> arg instanceof Handle handle && handle.getName().equals("OffsetCommand")));
        removeRange(register, previous(offset, 1), offset.getNext().getNext());
        clazz.methods.removeIf(m -> m.name.equals("OffsetCommand") || m.name.startsWith("lambda$OffsetCommand$"));
    }
    static void call(InsnList code, int opcode, String owner, String name, String descriptor) {
        code.add(new MethodInsnNode(opcode, owner, name, descriptor, opcode == INVOKEINTERFACE));
    }
    static void get(InsnList code, int opcode, String owner, String name, String descriptor) {
        code.add(new FieldInsnNode(opcode, owner, name, descriptor));
    }
    static void load(InsnList code, int local) { code.add(new VarInsnNode(ALOAD, local)); }
    static void patchRender(ClassNode clazz) {
        for (String renderer : List.of("renderHearts", "renderNumber")) {
            var method = method(clazz, renderer);
            var offset = find(method, node -> field(node, "display_offset"));
            var first = previous(offset, 3);
            method.instructions.insertBefore(first, new LdcInsnNode(HEIGHT));
            removeRange(method, first, offset.getNext());
            var override = find(method, node -> field(node, "force_higher_offset_for_players"));
            var hasLabel = (JumpInsnNode)previous(override, 4);
            var noLabel = (JumpInsnNode)override.getNext();
            require(hasLabel.getOpcode() == IFNE && noLabel.getOpcode() == IFEQ, "nameplate height branches");
            var after = hasLabel.label;
            method.instructions.set(hasLabel, new JumpInsnNode(IFEQ, noLabel.label));
            removeRange(method, previous(override, 3), after);
        }

        require(clazz.fields.removeIf(f -> f.name.equals("client")), "cached Minecraft client");
        removeAssignment(method(clazz, "<init>"), "client", 2);

        var render = method(clazz, "render");
        var oldClient = find(render, node -> node.getOpcode() == GETFIELD && field(node, "client"));
        render.instructions.remove(previous(oldClient, 1));
        render.instructions.set(oldClient, new MethodInsnNode(INVOKESTATIC, CLIENT, "method_1551", "()" + type(CLIENT), false));

        var indicator = find(render, node -> field(node, "indicator_type"));
        var entry = previous(indicator, 3);
        var exit = (JumpInsnNode)find(render, node -> node.getOpcode() == IFEQ);
        var hidden = exit.label;
        var clearance = new LabelNode();
        var code = new InsnList();

        // Apply vanilla label eligibility and block line of sight to other players.
        load(code, 1);
        code.add(new TypeInsnNode(INSTANCEOF, PLAYER));
        code.add(new JumpInsnNode(IFEQ, hidden));

        // Reject invisible and spectating players regardless of team visibility.
        load(code, 1);
        call(code, INVOKEVIRTUAL, ENTITY, "method_5767", "()Z");
        code.add(new JumpInsnNode(IFNE, hidden));
        load(code, 1);
        get(code, GETSTATIC, "net/minecraft/class_1294", "field_5905", "Lnet/minecraft/class_1291;");
        call(code, INVOKEVIRTUAL, "net/minecraft/class_1309", "method_6059", "(Lnet/minecraft/class_1291;)Z");
        code.add(new JumpInsnNode(IFNE, hidden));
        load(code, 1);
        call(code, INVOKEVIRTUAL, ENTITY, "method_7325", "()Z");
        code.add(new JumpInsnNode(IFNE, hidden));

        load(code, 1);
        call(code, INVOKESTATIC, CLIENT, "method_1551", "()" + type(CLIENT));
        get(code, GETFIELD, CLIENT, "field_1724", "Lnet/minecraft/class_746;");
        code.add(new JumpInsnNode(IF_ACMPEQ, clearance));

        load(code, 1);
        call(code, INVOKEVIRTUAL, ENTITY, "method_5715", "()Z");
        code.add(new JumpInsnNode(IFNE, hidden));
        load(code, 1);
        call(code, INVOKEVIRTUAL, ENTITY, "method_18276", "()Z");
        code.add(new JumpInsnNode(IFNE, hidden));
        load(code, 0);
        load(code, 1);
        call(code, INVOKEVIRTUAL, clazz.name, "method_3921", "(" + type(ENTITY) + ")Z");
        code.add(new JumpInsnNode(IFEQ, hidden));
        load(code, 0);
        get(code, GETFIELD, clazz.name, "field_4676", type(DISPATCHER));
        load(code, 1);
        call(code, INVOKEVIRTUAL, DISPATCHER, "method_23168", "(" + type(ENTITY) + ")D");
        code.add(new LdcInsnNode(4096.0));
        code.add(new InsnNode(DCMPG));
        code.add(new JumpInsnNode(IFGE, hidden));
        load(code, 0);
        get(code, GETFIELD, clazz.name, "field_4676", type(DISPATCHER));
        get(code, GETFIELD, DISPATCHER, "field_4686", type(CAMERA));
        code.add(new JumpInsnNode(IFNULL, hidden));

        load(code, 1);
        call(code, INVOKEVIRTUAL, ENTITY, "method_5770", "()" + type(WORLD));
        code.add(new TypeInsnNode(NEW, RAY));
        code.add(new InsnNode(DUP));
        load(code, 0);
        get(code, GETFIELD, clazz.name, "field_4676", type(DISPATCHER));
        get(code, GETFIELD, DISPATCHER, "field_4686", type(CAMERA));
        call(code, INVOKEVIRTUAL, CAMERA, "method_19326", "()" + type(VECTOR));
        load(code, 1);
        code.add(new VarInsnNode(FLOAD, 3));
        call(code, INVOKEVIRTUAL, ENTITY, "method_5836", "(F)" + type(VECTOR));
        get(code, GETSTATIC, RAY + "$class_3960", "field_17558", type(RAY + "$class_3960"));
        get(code, GETSTATIC, RAY + "$class_242", "field_1348", type(RAY + "$class_242"));
        load(code, 1);
        call(code, INVOKESPECIAL, RAY, "<init>", "(" + type(VECTOR).repeat(2) + type(RAY + "$class_3960") + type(RAY + "$class_242") + type(ENTITY) + ")V");
        call(code, INVOKEVIRTUAL, WORLD, "method_17742", "(" + type(RAY) + ")" + type(HIT));
        call(code, INVOKEVIRTUAL, HIT, "method_17783", "()" + type(HIT_TYPE));
        get(code, GETSTATIC, HIT_TYPE, "field_1333", type(HIT_TYPE));
        code.add(new JumpInsnNode(IF_ACMPNE, hidden));

        // Check only block collisions in the two-block volume above the entity.
        code.add(clearance);
        code.add(new FrameNode(F_SAME, 0, null, 0, null));
        load(code, 1);
        call(code, INVOKEVIRTUAL, ENTITY, "method_5770", "()" + type(WORLD));
        load(code, 1);
        load(code, 1);
        call(code, INVOKEVIRTUAL, ENTITY, "method_5829", "()" + type(BOX));
        code.add(new InsnNode(DUP));
        get(code, GETFIELD, BOX, "field_1325", "D");
        code.add(new LdcInsnNode(HEAD_CLEARANCE));
        code.add(new InsnNode(DADD));
        call(code, INVOKEVIRTUAL, BOX, "method_35578", "(D)" + type(BOX));
        code.add(new InsnNode(DUP));
        get(code, GETFIELD, BOX, "field_1325", "D");
        code.add(new LdcInsnNode(HEAD_CLEARANCE));
        code.add(new InsnNode(DSUB));
        call(code, INVOKEVIRTUAL, BOX, "method_35575", "(D)" + type(BOX));
        call(code, INVOKEVIRTUAL, WORLD, "method_20812", "(" + type(ENTITY) + type(BOX) + ")Ljava/lang/Iterable;");
        call(code, INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;");
        call(code, INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z");
        code.add(new JumpInsnNode(IFNE, hidden));
        render.instructions.insertBefore(entry, code);
    }
    static void patchTracker(ClassNode clazz) {
        var tick = method(clazz, "tick");
        var typeCheck = (TypeInsnNode)find(tick, node -> node instanceof TypeInsnNode test
                && test.getOpcode() == INSTANCEOF && test.desc.equals("net/minecraft/class_1309"));
        typeCheck.desc = PLAYER;

        var allowed = method(clazz, "isEntityTypeAllowed");
        var playersOption = find(allowed, node -> field(node, "players"));
        var begin = previous(playersOption, 3);
        while (begin.getPrevious() instanceof FrameNode || begin.getPrevious() instanceof LabelNode) {
            begin = begin.getPrevious();
        }
        removeRange(allowed, allowed.instructions.getFirst(), begin);
        var playerCheck = new InsnList();
        var player = new LabelNode();
        load(playerCheck, 0);
        playerCheck.add(new TypeInsnNode(INSTANCEOF, PLAYER));
        playerCheck.add(new JumpInsnNode(IFNE, player));
        playerCheck.add(new InsnNode(ICONST_0));
        playerCheck.add(new InsnNode(IRETURN));
        playerCheck.add(player);
        playerCheck.add(new FrameNode(F_SAME, 0, null, 0, null));
        if (begin instanceof LabelNode) {
            while (begin.getOpcode() < 0) {
                var next = begin.getNext();
                allowed.instructions.remove(begin);
                begin = next;
            }
        }
        allowed.instructions.insertBefore(begin, playerCheck);
    }
    static void patchConfig(ClassNode clazz) {
        var constructor = method(clazz, "<init>");
        HEIGHT_FIELDS.forEach(name -> removeAssignment(constructor, name, 2));
        clazz.fields.removeIf(f -> HEIGHT_FIELDS.contains(f.name));
    }
    static byte[] change(String path, byte[] data) throws AnalyzerException {
        if (path.endsWith("/en_us.json") || path.endsWith("/ru_ru.json")) {
            String lang = new String(data, StandardCharsets.UTF_8);
            lang = lang.replaceAll("(?m)^  \"[^\"]*(?:passive_mobs|hostile_mobs|angered_mobs|display_offset|offset_step_size|force_higher_offset_for_players|group\\.offset|increaseHeartOffset|decreaseHeartOffset)(?:\\.desc)?\":.*(?:\\R|$)", "");
            return lang.replaceAll(",(\\s*})", "$1").getBytes(StandardCharsets.UTF_8);
        }
        if (!path.endsWith(".class")) return data;
        var clazz = new ClassNode();
        new ClassReader(data).accept(clazz, ClassReader.SKIP_DEBUG);
        switch (clazz.name) {
            case ROOT + "HealthIndicatorsMod" -> patchClient(clazz);
            case ROOT + "commands/ModCommands" -> patchCommands(clazz);
            case ROOT + "mixin/NewEntityRendererMixin" -> patchRender(clazz);
            case CFG -> patchConfig(clazz);
            case ROOT + "RenderTracker" -> patchTracker(clazz);
            default -> { return data; }
        }
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        clazz.accept(writer);
        byte[] result = writer.toByteArray();
        var checked = new ClassNode();
        new ClassReader(result).accept(checked, ClassReader.EXPAND_FRAMES);
        for (var method : checked.methods) new Analyzer<>(new BasicVerifier()).analyze(checked.name, method);
        return result;
    }
    public static void main(String[] args) throws Exception {
        try (var input = new ZipFile(args[0]);
             var output = new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            for (var entry : Collections.list(input.entries())) {
                try (var contents = input.getInputStream(entry)) {
                    output.putNextEntry(new ZipEntry(entry.getName()));
                    output.write(change(entry.getName(), contents.readAllBytes()));
                    output.closeEntry();
                }
            }
        }
    }
}
