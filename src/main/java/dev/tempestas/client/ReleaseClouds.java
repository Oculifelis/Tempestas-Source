package dev.tempestas.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tempestas.Tempestas;
import dev.tempestas.net.CloudsPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Draws the cloud over each released patch as a heap of see-through boxes of mixed sizes that overlap and cut into each
 * other, off any grid, so the mass reads as soft layered vapour: dense in the middle over a few towers, thin and ragged
 * at the rim, bright tops, mid-tone sides and a dark underside, tinted by the sky's own cloud colour so it follows the
 * time of day. Boxes do not write depth, so overlaps blend. The shape comes from the patch position, so every player
 * sees the same cloud.
 */
public final class ReleaseClouds {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Tempestas.id("pipeline/release_cloud"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withShaderDefine("ALPHA_CUTOUT", 0.05F)
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();
    private static RenderType type;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int FADE_IN = 60;
    private static final int FADE_OUT = 80;
    /** Width of one cloud cube in blocks. */
    private static final float CELL = 1.5F;
    private static final float TOP = 1.0F, BOTTOM = 0.56F, SIDE_X = 0.78F, SIDE_Z = 0.88F;

    /** One box: centre offset and half sizes in cells, how lit it is by height, and how see-through it is. */
    private record Cube(float x, float y, float z, float hx, float hy, float hz, float light, float alpha, float phase) {
    }

    private static final class Live {
        final ResourceKey<Level> dimension;
        CloudsPayload.Entry entry;
        long born;
        long ends;
        List<Cube> cubes;

        Live(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    private static final Map<Long, Live> LIVE = new HashMap<>();

    private ReleaseClouds() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterRenderPipelinesEvent e) -> e.registerPipeline(PIPELINE));
        NeoForge.EVENT_BUS.addListener(ReleaseClouds::draw);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> LIVE.clear());
        CloudsPayload.receiver = ReleaseClouds::receive;
    }

    private static void receive(CloudsPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        for (CloudsPayload.Entry e : payload.clouds()) {
            long key = ((long) e.x() & 0x3FFFFFFL) << 38 | ((long) e.z() & 0x3FFFFFFL) << 12 | (e.y() & 0xFFFL);
            Live live = LIVE.get(key);
            // The key is the position only, so a cloud left over at the same spot in another dimension is replaced.
            if (live == null || live.dimension != level.dimension()) {
                live = new Live(level.dimension());
                LIVE.put(key, live);
            }
            if (live.cubes == null || !sameShape(live.entry, e)) {
                live.cubes = shape(e);
            }
            live.entry = e;
            live.born = now - (e.duration() - e.left());
            live.ends = now + e.left();
        }
    }

    private static boolean sameShape(CloudsPayload.Entry a, CloudsPayload.Entry b) {
        return a != null && a.radius() == b.radius() && a.spread() == b.spread() && a.thickness() == b.thickness();
    }

    /** Scatters overlapping boxes: most sit over a few towers, the rest spread thin toward a ragged rim. */
    private static List<Cube> shape(CloudsPayload.Entry e) {
        Random rand = new Random(((long) e.x() * 341873128712L) ^ ((long) e.z() * 132897987541L) ^ e.y());
        float reach = (e.radius() + e.spread() + 0.5F) / CELL;
        float thick = Math.max(2, e.thickness() * 1.4F / CELL);
        int towers = 3 + rand.nextInt(4);
        float[][] tower = new float[towers][3];
        for (int i = 0; i < towers; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rand.nextDouble()) * reach * 0.55;
            tower[i][0] = (float) (Math.cos(a) * d);
            tower[i][1] = (float) (Math.sin(a) * d);
            tower[i][2] = reach * (0.22F + rand.nextFloat() * 0.18F);
        }
        int count = Math.min(140, Math.round(10 + reach * reach * 0.9F));
        List<Cube> out = new ArrayList<>(count);
        for (int n = 0; n < count; n++) {
            float x, z;
            if (rand.nextFloat() < 0.6F) {
                float[] t = tower[rand.nextInt(towers)];
                x = t[0] + (float) rand.nextGaussian() * t[2];
                z = t[1] + (float) rand.nextGaussian() * t[2];
            } else {
                double a = rand.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(rand.nextDouble()) * reach;
                x = (float) (Math.cos(a) * d);
                z = (float) (Math.sin(a) * d);
            }
            float edge = (float) Math.sqrt(x * x + z * z) / reach;
            if (edge > 1.05F) {
                n--;
                continue;
            }
            float lump = 0;
            for (float[] t : tower) {
                float dx = x - t[0], dz = z - t[1];
                lump = Math.max(lump, (float) Math.exp(-(dx * dx + dz * dz) / (2 * t[2] * t[2])));
            }
            float dome = (float) Math.sqrt(Math.max(0, 1 - Math.min(1, edge) * Math.min(1, edge)));
            float column = Math.max(0.8F, thick * dome * (0.35F + 0.8F * lump));
            float hx = 0.9F + rand.nextFloat() * 1.8F * (1.2F - edge * 0.5F);
            float hz = 0.9F + rand.nextFloat() * 1.8F * (1.2F - edge * 0.5F);
            float hy = Math.min(column * 0.5F, 0.5F + rand.nextFloat() * 1.1F);
            float y = hy + rand.nextFloat() * Math.max(0, column - 2 * hy) + (rand.nextFloat() < 0.25F ? 0.3F : 0);
            float h = (y + hy) / Math.max(1, thick);
            float light = 0.74F + 0.26F * Math.min(1, h) + rand.nextFloat() * 0.03F;
            float alpha = (0.30F + rand.nextFloat() * 0.12F + 0.12F * lump) * (1 - Math.max(0, edge - 0.6F) * 1.4F);
            if (alpha > 0.04F) {
                out.add(new Cube(x, y, z, hx, hy, hz, light, alpha, rand.nextFloat() * 6.2832F));
            }
        }
        return out;
    }

    private static RenderType type() {
        if (type == null) {
            type = RenderType.create("tempestas_release_cloud", RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", Tempestas.id("textures/misc/cloud_cube.png")).sortOnUpload().createRenderSetup());
        }
        return type;
    }

    private static void draw(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            LIVE.clear();
            return;
        }
        if (LIVE.isEmpty()) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partial;
        List<Live> shown = new ArrayList<>();
        Iterator<Live> it = LIVE.values().iterator();
        while (it.hasNext()) {
            Live live = it.next();
            if (now > live.ends) {
                it.remove();
            } else if (live.dimension == level.dimension()) {
                shown.add(live);
            }
        }
        if (shown.isEmpty()) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        int sky = camera.attributeProbe().getValue(EnvironmentAttributes.CLOUD_COLOR, partial);
        float sr = (sky >> 16 & 255) / 255F, sg = (sky >> 8 & 255) / 255F, sb = (sky & 255) / 255F;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), type(), (pose, buffer) -> {
            for (Live live : shown) {
                emit(pose, buffer, live, now, cam, sr, sg, sb);
            }
        });
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer b, Live live, double now, Vec3 cam, float sr, float sg, float sb) {
        CloudsPayload.Entry e = live.entry;
        float life = (float) Math.min(Mth.clamp((now - live.born) / FADE_IN, 0, 1), Mth.clamp((live.ends - now) / FADE_OUT, 0, 1));
        if (life <= 0.01F) {
            return;
        }
        float grow = 0.6F + 0.4F * life;
        float s = CELL * grow;
        float dark = 1 - 0.62F * e.darkness();
        // Storm clouds lean a little blue-grey.
        float br = sr * dark, bg = sg * dark, bb = sb * Math.min(1, dark + 0.06F * e.darkness());
        float cx = (float) (e.x() + 0.5 - cam.x), cy = (float) (e.y() + e.height() - cam.y), cz = (float) (e.z() + 0.5 - cam.z);
        for (Cube c : live.cubes) {
            // each box slowly swells, shrinks and drifts, so the heap churns like vapour
            float ph = c.phase();
            float breathe = 1 + 0.22F * Mth.sin((float) (now * 0.021) + ph);
            float px = c.x() + 0.45F * Mth.sin((float) (now * 0.009) + ph * 1.7F);
            float py = c.y() + 0.18F * Mth.sin((float) (now * 0.013) + ph * 2.3F);
            float pz = c.z() + 0.45F * Mth.cos((float) (now * 0.008) + ph * 1.3F);
            float hx = c.hx() * breathe, hy = c.hy() * (2 - breathe) * 0.5F + c.hy() * 0.5F, hz = c.hz() * breathe;
            float x0 = cx + (px - hx) * s, x1 = cx + (px + hx) * s;
            float y0 = cy + (py - hy) * s, y1 = cy + (py + hy) * s;
            float z0 = cz + (pz - hz) * s, z1 = cz + (pz + hz) * s;
            float a = c.alpha() * life;
            float l = c.light();
            int top = argb(a, br * l * TOP, bg * l * TOP, bb * l * TOP);
            int bottom = argb(a, br * l * BOTTOM, bg * l * BOTTOM, bb * l * BOTTOM);
            int sideX = argb(a, br * l * SIDE_X, bg * l * SIDE_X, bb * l * SIDE_X);
            int sideZ = argb(a, br * l * SIDE_Z, bg * l * SIDE_Z, bb * l * SIDE_Z);
            quad(b, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, top, 0, 1, 0);
            quad(b, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, bottom, 0, -1, 0);
            quad(b, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, sideX, -1, 0, 0);
            quad(b, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, sideX, 1, 0, 0);
            quad(b, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, sideZ, 0, 0, -1);
            quad(b, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, sideZ, 0, 0, 1);
        }
    }

    private static void quad(VertexConsumer b, PoseStack.Pose p, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int argb, float nx, float ny, float nz) {
        v(b, p, ax, ay, az, 0, 1, argb, nx, ny, nz);
        v(b, p, bx, by, bz, 1, 1, argb, nx, ny, nz);
        v(b, p, cx, cy, cz, 1, 0, argb, nx, ny, nz);
        v(b, p, dx, dy, dz, 0, 0, argb, nx, ny, nz);
    }

    private static int argb(float a, float r, float g, float b) {
        return Mth.clamp(Math.round(a * 255), 0, 255) << 24 | Mth.clamp(Math.round(r * 255), 0, 255) << 16
                | Mth.clamp(Math.round(g * 255), 0, 255) << 8 | Mth.clamp(Math.round(b * 255), 0, 255);
    }

    private static void v(VertexConsumer b, PoseStack.Pose p, float x, float y, float z, float u, float v, int argb,
                          float nx, float ny, float nz) {
        b.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }
}
