package com.trollmod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.trollmod.TrollMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a Blockbench / Bedrock style model (caine.geo.json) and its animations
 * (caine.animation.json) and draws it in place of the player.
 * Only the "idle" and "walk" animations are used.
 */
public final class CaineModel {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation GEO_FILE =
            new ResourceLocation(TrollMod.MODID, "geo/caine.geo.json");
    private static final ResourceLocation ANIMATION_FILE =
            new ResourceLocation(TrollMod.MODID, "animations/caine.animation.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TrollMod.MODID, "textures/entity/caine.png");

    /** The model is about 3.8 blocks tall at 1.0; 0.5 makes it roughly player-sized. Change to resize. */
    public static final float SCALE = 0.5F;

    /** Horizontal speed (blocks per tick) above which the "walk" animation is used. */
    private static final double WALK_THRESHOLD_SQ = 0.02D * 0.02D;

    private static final String[] FACE_NAMES = {"north", "south", "east", "west", "up", "down"};

    private static CaineModel instance;
    private static boolean loadFailed;

    // ------------------------------------------------------------------ data

    private record Vertex(float x, float y, float z, float u, float v) {
    }

    private record Quad(Vertex[] vertices, float nx, float ny, float nz) {
    }

    private static final class Bone {
        final String name;
        float px, py, pz;       // pivot (x already mirrored)
        float rx, ry, rz;       // rest rotation in degrees, as written in the file
        final List<Quad> quads = new ArrayList<>();
        final List<Bone> children = new ArrayList<>();

        Bone(String name) {
            this.name = name;
        }
    }

    private record Key(float time, float x, float y, float z, boolean smooth) {
    }

    private static final class Track {
        final List<Key> keys = new ArrayList<>();
    }

    private static final class BoneAnimation {
        Track rotation;
        Track position;
        Track scale;
    }

    private static final class Animation {
        float length;
        boolean loop;
        final Map<String, BoneAnimation> bones = new HashMap<>();
    }

    private final List<Bone> roots = new ArrayList<>();
    private Animation idle;
    private Animation walk;

    private CaineModel() {
    }

    /** Returns the loaded model, or null if the files could not be read (error is logged once). */
    public static CaineModel get() {
        if (instance == null && !loadFailed) {
            try {
                instance = load();
            } catch (Exception e) {
                loadFailed = true;
                LOGGER.error("[trollmod] Could not load the КЕЙН model; the normal player model is used instead", e);
            }
        }
        return instance;
    }

    // --------------------------------------------------------------- loading

    private static CaineModel load() throws IOException {
        CaineModel model = new CaineModel();

        JsonObject geo = readJson(GEO_FILE).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geo.getAsJsonObject("description");
        float texW = description.get("texture_width").getAsFloat();
        float texH = description.get("texture_height").getAsFloat();

        Map<String, Bone> bones = new LinkedHashMap<>();
        Map<String, String> parents = new HashMap<>();
        for (JsonElement element : geo.getAsJsonArray("bones")) {
            JsonObject json = element.getAsJsonObject();
            Bone bone = new Bone(json.get("name").getAsString());
            float[] pivot = floats(json, "pivot", 3);
            float[] rotation = floats(json, "rotation", 3);
            bone.px = -pivot[0];            // the model is mirrored on X, like Blockbench -> Minecraft
            bone.py = pivot[1];
            bone.pz = pivot[2];
            bone.rx = rotation[0];
            bone.ry = rotation[1];
            bone.rz = rotation[2];
            if (json.has("cubes")) {
                for (JsonElement cube : json.getAsJsonArray("cubes")) {
                    addCube(bone, cube.getAsJsonObject(), texW, texH);
                }
            }
            bones.put(bone.name, bone);
            if (json.has("parent")) {
                parents.put(bone.name, json.get("parent").getAsString());
            }
        }
        for (Bone bone : bones.values()) {
            Bone parent = parents.containsKey(bone.name) ? bones.get(parents.get(bone.name)) : null;
            if (parent != null) {
                parent.children.add(bone);
            } else {
                model.roots.add(bone);
            }
        }

        JsonObject animations = readJson(ANIMATION_FILE).getAsJsonObject("animations");
        model.idle = parseAnimation(animations, "idle");
        model.walk = parseAnimation(animations, "walk");
        return model;
    }

    private static JsonObject readJson(ResourceLocation id) throws IOException {
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(id)
                .orElseThrow(() -> new IOException("Missing resource " + id));
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static float[] floats(JsonObject json, String key, int count) {
        float[] out = new float[count];
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray array = json.getAsJsonArray(key);
            for (int i = 0; i < count && i < array.size(); i++) {
                out[i] = array.get(i).getAsFloat();
            }
        }
        return out;
    }

    // ----------------------------------------------------------------- cubes

    private static void addCube(Bone bone, JsonObject cube, float texW, float texH) {
        float[] o = floats(cube, "origin", 3);
        float[] s = floats(cube, "size", 3);
        float x0 = o[0], y0 = o[1], z0 = o[2];
        float x1 = x0 + s[0], y1 = y0 + s[1], z1 = z0 + s[2];

        // Texture rectangles per face: {u, v, width, height}; null = face not drawn.
        float[][] uv = new float[6][];
        JsonElement uvElement = cube.get("uv");
        if (uvElement != null && uvElement.isJsonObject()) {
            JsonObject faces = uvElement.getAsJsonObject();
            for (int i = 0; i < 6; i++) {
                if (faces.has(FACE_NAMES[i])) {
                    JsonObject face = faces.getAsJsonObject(FACE_NAMES[i]);
                    float[] origin = floats(face, "uv", 2);
                    float[] size = floats(face, "uv_size", 2);
                    uv[i] = new float[]{origin[0], origin[1], size[0], size[1]};
                }
            }
        } else if (uvElement != null && uvElement.isJsonArray()) {
            // Old "box UV" layout: one origin, faces unfolded around it.
            JsonArray array = uvElement.getAsJsonArray();
            float u = array.get(0).getAsFloat();
            float v = array.get(1).getAsFloat();
            float w = s[0], h = s[1], d = s[2];
            uv[0] = new float[]{u + d, v + d, w, h};              // north
            uv[1] = new float[]{u + d + w + d, v + d, w, h};      // south
            uv[2] = new float[]{u, v + d, d, h};                  // east
            uv[3] = new float[]{u + d + w, v + d, d, h};          // west
            uv[4] = new float[]{u + d, v, w, d};                  // up
            uv[5] = new float[]{u + d + w, v + d, w, -d};         // down
        }

        // Corner order for every face: top-left, top-right, bottom-right, bottom-left,
        // as seen from outside the cube. Positions are in the file's coordinates.
        float[][][] corners = {
                {{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}},   // north
                {{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}},   // south
                {{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}},   // east
                {{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}},   // west
                {{x1, y1, z1}, {x0, y1, z1}, {x0, y1, z0}, {x1, y1, z0}},   // up
                {{x1, y0, z0}, {x0, y0, z0}, {x0, y0, z1}, {x1, y0, z1}},   // down
        };
        float[][] normals = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

        for (int i = 0; i < 6; i++) {
            if (uv[i] == null) {
                continue;
            }
            float u0 = uv[i][0], v0 = uv[i][1], w = uv[i][2], h = uv[i][3];
            float[][] texCoords = {{u0, v0}, {u0 + w, v0}, {u0 + w, v0 + h}, {u0, v0 + h}};
            Vertex[] vertices = new Vertex[4];
            for (int c = 0; c < 4; c++) {
                float[] p = corners[i][c];
                vertices[c] = new Vertex(-p[0], p[1], p[2],              // mirror X
                        texCoords[c][0] / texW, texCoords[c][1] / texH);
            }
            bone.quads.add(new Quad(vertices, -normals[i][0], normals[i][1], normals[i][2]));
        }
    }

    // ------------------------------------------------------------ animations

    private static Animation parseAnimation(JsonObject animations, String name) throws IOException {
        if (!animations.has(name)) {
            throw new IOException("Animation '" + name + "' not found in " + ANIMATION_FILE);
        }
        JsonObject json = animations.getAsJsonObject(name);
        Animation animation = new Animation();
        animation.length = json.has("animation_length") ? json.get("animation_length").getAsFloat() : 0.0F;
        animation.loop = json.has("loop") && json.get("loop").isJsonPrimitive()
                && json.get("loop").getAsJsonPrimitive().isBoolean() && json.get("loop").getAsBoolean();
        if (json.has("bones")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("bones").entrySet()) {
                JsonObject channels = entry.getValue().getAsJsonObject();
                BoneAnimation bone = new BoneAnimation();
                bone.rotation = parseTrack(channels.get("rotation"));
                bone.position = parseTrack(channels.get("position"));
                bone.scale = parseTrack(channels.get("scale"));
                animation.bones.put(entry.getKey(), bone);
            }
        }
        return animation;
    }

    private static Track parseTrack(JsonElement element) {
        Track track = new Track();
        if (element == null) {
            return track;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                float time;
                try {
                    time = Float.parseFloat(entry.getKey());
                } catch (NumberFormatException e) {
                    continue;
                }
                JsonElement frame = entry.getValue();
                boolean smooth = false;
                JsonElement vector = frame;
                if (frame.isJsonObject()) {
                    JsonObject object = frame.getAsJsonObject();
                    smooth = object.has("lerp_mode")
                            && "catmullrom".equals(object.get("lerp_mode").getAsString());
                    JsonElement value = object.has("post") ? object.get("post") : object.get("pre");
                    if (value == null) {
                        value = object;
                    }
                    vector = value.isJsonObject() ? value.getAsJsonObject().get("vector") : value;
                }
                float[] v = vec3(vector);
                if (v != null) {
                    track.keys.add(new Key(time, v[0], v[1], v[2], smooth));
                }
            }
            track.keys.sort(Comparator.comparingDouble(Key::time));
        } else {
            float[] v = vec3(element);
            if (v != null) {
                track.keys.add(new Key(0.0F, v[0], v[1], v[2], false));
            }
        }
        return track;
    }

    private static float[] vec3(JsonElement element) {
        if (element == null) {
            return null;
        }
        if (element.isJsonArray() && element.getAsJsonArray().size() >= 3) {
            JsonArray array = element.getAsJsonArray();
            return new float[]{number(array.get(0)), number(array.get(1)), number(array.get(2))};
        }
        if (element.isJsonPrimitive()) {
            float n = number(element);
            return new float[]{n, n, n};
        }
        return null;
    }

    /** Numbers only; anything else (e.g. a Molang expression) counts as 0. */
    private static float number(JsonElement element) {
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsFloat();
            }
            try {
                return Float.parseFloat(element.getAsString());
            } catch (NumberFormatException ignored) {
                return 0.0F;
            }
        }
        return 0.0F;
    }

    /** Writes the track value at the given time into out (left untouched if the track is empty). */
    private static void sample(Track track, float time, float[] out) {
        if (track == null || track.keys.isEmpty()) {
            return;
        }
        List<Key> keys = track.keys;
        int n = keys.size();
        if (n == 1 || time <= keys.get(0).time()) {
            copy(keys.get(0), out);
            return;
        }
        if (time >= keys.get(n - 1).time()) {
            copy(keys.get(n - 1), out);
            return;
        }
        int i = 0;
        while (i + 1 < n && keys.get(i + 1).time() <= time) {
            i++;
        }
        Key a = keys.get(i);
        Key b = keys.get(i + 1);
        float f = (time - a.time()) / (b.time() - a.time());
        if (a.smooth() || b.smooth()) {
            Key p0 = i > 0 ? keys.get(i - 1) : a;
            Key p3 = i + 2 < n ? keys.get(i + 2) : b;
            out[0] = catmullRom(p0.x(), a.x(), b.x(), p3.x(), f);
            out[1] = catmullRom(p0.y(), a.y(), b.y(), p3.y(), f);
            out[2] = catmullRom(p0.z(), a.z(), b.z(), p3.z(), f);
        } else {
            out[0] = Mth.lerp(f, a.x(), b.x());
            out[1] = Mth.lerp(f, a.y(), b.y());
            out[2] = Mth.lerp(f, a.z(), b.z());
        }
    }

    private static void copy(Key key, float[] out) {
        out[0] = key.x();
        out[1] = key.y();
        out[2] = key.z();
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        return 0.5F * ((2.0F * p1)
                + (-p0 + p2) * t
                + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t * t
                + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t * t * t);
    }

    // ------------------------------------------------------------- rendering

    public void render(Player player, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        Animation animation = (dx * dx + dz * dz) > WALK_THRESHOLD_SQ ? this.walk : this.idle;

        float seconds = (player.tickCount + partialTick) / 20.0F;
        float time;
        if (animation.length <= 0.0F) {
            time = 0.0F;
        } else if (animation.loop) {
            time = seconds % animation.length;
        } else {
            time = Math.min(seconds, animation.length);
        }

        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.scale(SCALE, SCALE, SCALE);
        for (Bone root : this.roots) {
            renderBone(root, animation, time, poseStack, consumer, light, overlay);
        }
        poseStack.popPose();
    }

    private void renderBone(Bone bone, Animation animation, float time, PoseStack poseStack,
                            VertexConsumer consumer, int light, int overlay) {
        float[] rotation = {0.0F, 0.0F, 0.0F};
        float[] position = {0.0F, 0.0F, 0.0F};
        float[] scale = {1.0F, 1.0F, 1.0F};
        BoneAnimation boneAnimation = animation.bones.get(bone.name);
        if (boneAnimation != null) {
            sample(boneAnimation.rotation, time, rotation);
            sample(boneAnimation.position, time, position);
            sample(boneAnimation.scale, time, scale);
        }
        // A bone scaled to zero is hidden, together with everything attached to it.
        if (Math.abs(scale[0]) < 1.0E-4F || Math.abs(scale[1]) < 1.0E-4F || Math.abs(scale[2]) < 1.0E-4F) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(-position[0] / 16.0F, position[1] / 16.0F, position[2] / 16.0F);
        poseStack.translate(bone.px / 16.0F, bone.py / 16.0F, bone.pz / 16.0F);

        float rotX = (float) Math.toRadians(-(bone.rx + rotation[0]));
        float rotY = (float) Math.toRadians(-(bone.ry + rotation[1]));
        float rotZ = (float) Math.toRadians(bone.rz + rotation[2]);
        if (rotZ != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotation(rotZ));
        }
        if (rotY != 0.0F) {
            poseStack.mulPose(Axis.YP.rotation(rotY));
        }
        if (rotX != 0.0F) {
            poseStack.mulPose(Axis.XP.rotation(rotX));
        }
        if (scale[0] != 1.0F || scale[1] != 1.0F || scale[2] != 1.0F) {
            poseStack.scale(scale[0], scale[1], scale[2]);
        }
        poseStack.translate(-bone.px / 16.0F, -bone.py / 16.0F, -bone.pz / 16.0F);

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        for (Quad quad : bone.quads) {
            for (Vertex v : quad.vertices()) {
                consumer.vertex(matrix, v.x() / 16.0F, v.y() / 16.0F, v.z() / 16.0F)
                        .color(1.0F, 1.0F, 1.0F, 1.0F)
                        .uv(v.u(), v.v())
                        .overlayCoords(overlay)
                        .uv2(light)
                        .normal(normal, quad.nx(), quad.ny(), quad.nz())
                        .endVertex();
            }
        }

        for (Bone child : bone.children) {
            renderBone(child, animation, time, poseStack, consumer, light, overlay);
        }
        poseStack.popPose();
    }
}
