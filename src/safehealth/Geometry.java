package safehealth;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_2350;
import net.minecraft.class_4587;
import net.minecraft.class_5819;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1922;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_638;
import net.minecraft.class_8113;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** World-space occlusion for the Fabric 1.20.1 intermediary namespace. */
public final class Geometry {
    private static final double HEADROOM = 2.0;
    private static final double SCORE_LINE_HEIGHT = 9.0 * 1.15 * 0.025;
    private static final List<class_238> displayBoxes = new ArrayList<>();
    private static final Set<class_8113> loadedDisplays = new HashSet<>();
    private static class_638 indexedWorld;
    private static boolean cachedDisplays;
    private static boolean unknownDisplay;
    private static class_638 cachedWorld;
    private static float cachedDelta;

    static {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (world == indexedWorld && entity instanceof class_8113 display) {
                loadedDisplays.add(display);
                cachedDisplays = false;
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (world == indexedWorld && loadedDisplays.remove(entity)) cachedDisplays = false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (indexedWorld != null && indexedWorld != client.field_1687) {
                indexedWorld = null;
                cachedWorld = null;
                loadedDisplays.clear();
                displayBoxes.clear();
                cachedDisplays = false;
            }
        });
        WorldRenderEvents.START.register(context -> {
            cachedDisplays = false;
            cachedWorld = null;
            displayBoxes.clear();
        });
    }

    private Geometry() {}

    public static boolean visible(class_1309 target, float delta, class_310 client) {
        if (client == null || client.field_1724 == null || client.field_1687 == null ||
                target == null || target.method_5770() != client.field_1687 || !Float.isFinite(delta)) return false;
        var world = client.field_1687;
        var camera = client.field_1773.method_19418();
        if (camera == null || !camera.method_19332()) return false;

        double dx = (target.field_6038 - target.method_23317()) * (1.0 - delta);
        double dy = (target.field_5971 - target.method_23318()) * (1.0 - delta);
        double dz = (target.field_5989 - target.method_23321()) * (1.0 - delta);
        var body = target.method_5829();
        var headroom = body.method_35578(body.field_1325 + HEADROOM).method_35575(body.field_1325);
        var drawnHeadroom = headroom.method_989(dx, dy, dz);
        if (world.method_20812(target, headroom).iterator().hasNext() ||
                ((dx != 0 || dy != 0 || dz != 0) && world.method_20812(target, drawnHeadroom).iterator().hasNext())) return false;

        var targetEye = target.method_5836(delta);
        var cameraEye = camera.method_19326();
        var viewerEye = client.field_1724.method_5836(delta);
        double x = target.method_23317() + dx;
        double z = target.method_23321() + dz;
        double labelY = target.method_23318() + dy + target.method_51152();
        var baseLabel = new class_243(x, labelY, z);
        boolean hasScore = cameraEye.method_1025(new class_243(target.method_23317(), target.method_23318(), target.method_23321())) < 100.0 &&
                world.method_8428().method_1189(2) != null;
        var nameLabel = hasScore ? new class_243(x, labelY + SCORE_LINE_HEIGHT, z) : baseLabel;

        refreshDisplays(world, delta, client, cameraEye);
        if (unknownDisplay) return false;
        for (var box : displayBoxes) {
            if (box.method_994(headroom) || box.method_994(drawnHeadroom)) return false;
        }
        return clearFrom(world, client.field_1724, viewerEye, targetEye, baseLabel, nameLabel) &&
                (cameraEye.method_1025(viewerEye) < 1.0e-14 ||
                clearFrom(world, client.field_1724, cameraEye, targetEye, baseLabel, nameLabel));
    }

    private static boolean clearFrom(class_638 world, class_1297 viewer, class_243 from,
                                     class_243 eye, class_243 baseLabel, class_243 nameLabel) {
        return clear(world, viewer, from, eye) && clear(world, viewer, from, baseLabel) &&
                (baseLabel == nameLabel || clear(world, viewer, from, nameLabel));
    }

    private static boolean clear(class_638 world, class_1297 viewer, class_243 from, class_243 to) {
        if (!world.method_8316(class_2338.method_49638(from)).method_15769() ||
                !world.method_8316(class_2338.method_49638(to)).method_15769()) return false;
        for (var box : displayBoxes) {
            if (box.method_1006(from) || box.method_1006(to) || box.method_992(from, to).isPresent()) return false;
        }
        return misses(world, viewer, from, to, class_3959.class_3960.field_17558) &&
                misses(world, viewer, from, to, class_3959.class_3960.field_17559);
    }

    private static boolean misses(class_1922 world, class_1297 viewer, class_243 from, class_243 to,
                                  class_3959.class_3960 shape) {
        var ray = new class_3959(from, to, shape, class_3959.class_242.field_1347, viewer);
        return world.method_17742(ray).method_17783() == class_239.class_240.field_1333;
    }

    private static void refreshDisplays(class_638 world, float delta, class_310 client, class_243 camera) {
        if (indexedWorld != world) {
            loadedDisplays.clear();
            for (var entity : world.method_18112()) {
                if (entity instanceof class_8113 display) loadedDisplays.add(display);
            }
            indexedWorld = world;
            cachedDisplays = false;
        }
        if (cachedDisplays && cachedWorld == world && delta == cachedDelta) return;
        cachedWorld = world;
        cachedDelta = delta;
        cachedDisplays = true;
        unknownDisplay = false;
        displayBoxes.clear();
        for (var display : loadedDisplays) {
            if (!display.method_5727(camera.field_1352, camera.field_1351, camera.field_1350) ||
                    display.method_49777() == null) continue;
            var box = displayBox(display, delta, client);
            if (box == null) {
                unknownDisplay = true;
                return;
            }
            displayBoxes.add(box);
        }
    }

    private static class_238 localBounds(class_8113 display, class_310 client) {
        if (display instanceof class_8113.class_8115) return new class_238(0, 0, 0, 1, 1, 1);
        if (display instanceof class_8113.class_8123 text) {
            var data = text.method_49784();
            if (data == null) return new class_238(0, 0, 0, 0, 0, 0);
            var lines = client.field_1772.method_1728(data.comp_1334(), data.comp_1335());
            int width = 0;
            for (var line : lines) width = Math.max(width, client.field_1772.method_30880(line));
            // Vanilla centers the lines in X and anchors the paragraph at the bottom.
            // Allow extra glyph overhang, the background edges and the shadow.
            double halfWidth = (width / 2.0 + 8) * .025;
            return new class_238(-halfWidth, -.2, -.025, halfWidth, (10.0 * lines.size() + 8) * .025, .025);
        }
        if (display instanceof class_8113.class_8122 item) return itemBounds(item, client);
        return null;
    }

    private static class_238 itemBounds(class_8113.class_8122 item, class_310 client) {
        var data = item.method_49782();
        if (data == null || data.comp_1322().method_7960()) return new class_238(0, 0, 0, 0, 0, 0);
        var model = client.method_1480().method_4019(data.comp_1322(), item.method_5770(), null, item.method_5628());
        var matrices = new class_4587();
        model.method_4709().method_3503(data.comp_1323()).method_23075(false, matrices);
        var matrix = new Matrix4f().rotateY((float)Math.PI).mul(matrices.method_23760().method_23761()).translate(-.5f, -.5f, -.5f);
        var random = class_5819.method_43049(42);
        var faces = class_2350.values();
        var low = new Vector3f(Float.POSITIVE_INFINITY);
        var high = new Vector3f(Float.NEGATIVE_INFINITY);
        var v = new Vector3f();
        if (!model.method_4713()) {
            for (int f = 0; f <= faces.length; f++) {
                random.method_43052(42);
                for (var quad : model.method_4707(null, f == faces.length ? null : faces[f], random)) {
                    int[] vertices = quad.method_3357();
                    int stride = vertices.length / 4;
                    if (stride < 3 || vertices.length % 4 != 0) return null;
                    for (int i = 0; i < vertices.length; i += stride) {
                        v.set(Float.intBitsToFloat(vertices[i]), Float.intBitsToFloat(vertices[i+1]), Float.intBitsToFloat(vertices[i+2]));
                        matrix.transformPosition(v);
                        if (!v.isFinite()) return null;
                        low.min(v); high.max(v);
                    }
                }
            }
        }
        if (!low.isFinite()) {
            // Vanilla built-in models supply no baked vertices. Bound their model-local
            // drawing space conservatively, then apply the actual item/display transforms.
            for (int i=0; i<8; i++) {
                v.set((i&1)==0 ? -4:4, (i&2)==0 ? -4:4, (i&4)==0 ? -4:4);
                matrix.transformPosition(v);
                if (!v.isFinite()) return null;
                low.min(v); high.max(v);
            }
        }
        return new class_238(low.x,low.y,low.z,high.x,high.y,high.z).method_1014(.025);
    }

    private static class_238 displayBox(class_8113 display, float delta, class_310 client) {
        // Client tracking dimensions can be zero. Start from the visual model and apply
        // its animated translation, scale and rotation, even for an off-screen origin.
        var state = display.method_49777();
        var local = localBounds(display, client);
        if (state == null || local == null) return null;
        float progress = display.method_48844(delta);
        Matrix4f transform = state.comp_1328().method_48888(progress).method_22936();
        var fixed = state.comp_1329() == class_8113.class_8114.field_42406;
        double x = display.field_6038 + delta * (display.method_23317() - display.field_6038);
        double y = display.field_5971 + delta * (display.method_23318() - display.field_5971);
        double z = display.field_5989 + delta * (display.method_23321() - display.field_5989);
        double lowX = Double.POSITIVE_INFINITY, lowY = lowX, lowZ = lowX;
        double highX = Double.NEGATIVE_INFINITY, highY = highX, highZ = highX;
        double radius = 0.0;
        Vector3f corner = new Vector3f();
        for (int i = 0; i < 8; i++) {
            corner.set((float)((i & 1) == 0 ? local.field_1323 : local.field_1320),
                    (float)((i & 2) == 0 ? local.field_1322 : local.field_1325),
                    (float)((i & 4) == 0 ? local.field_1321 : local.field_1324));
            transform.transformPosition(corner);
            if (fixed) display.method_48863().transform(corner);
            if (!corner.isFinite()) return null;
            lowX = Math.min(lowX, corner.x); highX = Math.max(highX, corner.x);
            lowY = Math.min(lowY, corner.y); highY = Math.max(highY, corner.y);
            lowZ = Math.min(lowZ, corner.z); highZ = Math.max(highZ, corner.z);
            radius = Math.max(radius, corner.length());
        }
        if (!Double.isFinite(radius)) return null;
        class_238 visualBounds = fixed
                ? new class_238(x + lowX, y + lowY, z + lowZ, x + highX, y + highY, z + highZ)
                : new class_238(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        // A zero-sized display disables frustum culling and can retain bounds at its spawn origin.
        if (!display.field_5985) visualBounds = visualBounds.method_991(display.method_5830());
        return visualBounds.method_1014(0.001);
    }
}
