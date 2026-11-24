import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;

final class OptimizeTracker {
    private OptimizeTracker() {}

    static void patch(ClassNode tracker) {
        MethodNode tick = method(tracker, "tick");
        int changed = 0;
        for (AbstractInsnNode insn : tick.instructions.toArray()) {
            if (insn instanceof MethodInsnNode call && call.name.equals("method_18112")) {
                // ClientWorld.getPlayers() is already maintained by the client.
                call.name = "method_18456";
                call.desc = "()Ljava/util/List;";
                changed++;
            }
        }
        if (changed != 1) throw new IllegalStateException("Expected one entity loop in tick");

        // The original trim loop scanned every entity for each tracked UUID.
        // Use the engine's live UUID index; unloading or changing worlds needs no cache flush.
        MethodNode lookup = method(tracker, "getEntityFromUUID");
        lookup.instructions.clear();
        lookup.tryCatchBlocks.clear();
        if (lookup.localVariables != null) lookup.localVariables.clear();
        InsnList body = lookup.instructions;
        body.add(new VarInsnNode(ALOAD, 1));
        body.add(new TypeInsnNode(CHECKCAST, "safehealth/mixin/ClientWorldLookup"));
        body.add(new MethodInsnNode(INVOKEINTERFACE, "safehealth/mixin/ClientWorldLookup",
                "healthIndicators$getEntityLookup", "()Lnet/minecraft/class_5577;", true));
        body.add(new VarInsnNode(ALOAD, 0));
        body.add(new MethodInsnNode(INVOKEINTERFACE, "net/minecraft/class_5577", "method_31808",
                "(Ljava/util/UUID;)Lnet/minecraft/class_5568;", true));
        body.add(new TypeInsnNode(CHECKCAST, "net/minecraft/class_1297"));
        body.add(new InsnNode(ARETURN));
    }

    static void patchConfig(ClassNode config) {
        // Holding the override key used to rewrite the same JSON every client tick.
        skipUnchanged(config, "setOverrideAllFiltersEnabled", "getOverrideAllFiltersEnabled");
        skipUnchanged(config, "setRenderingEnabled", "getRenderingEnabled");
    }

    private static void skipUnchanged(ClassNode config, String setter, String getter) {
        MethodNode target = method(config, setter);
        LabelNode changed = new LabelNode();
        InsnList check = new InsnList();
        check.add(new VarInsnNode(ILOAD, 0));
        check.add(new MethodInsnNode(INVOKESTATIC, config.name, getter, "()Z", false));
        check.add(new JumpInsnNode(IF_ICMPNE, changed));
        check.add(new InsnNode(RETURN));
        check.add(changed);
        check.add(new FrameNode(F_SAME, 0, null, 0, null));
        target.instructions.insert(check);
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(m -> name.equals(m.name)).findFirst().orElseThrow();
    }
}
