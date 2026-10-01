package com.max.pz3dfix;

import com.pavelvoronin.pz3d.Geometry;
import com.pavelvoronin.pz3d.Renderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import zombie.characters.IsoPlayer;
import zombie.core.textures.Texture;
import zombie.iso.IsoDirections;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
import zombie.iso.sprite.IsoSprite;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class InteractiveLayer {

    private static final InteractiveLayer INSTANCE = new InteractiveLayer();
    public static InteractiveLayer getInstance() { return INSTANCE; }

    public static final float DOOR_THICKNESS = 0.035f;
    public static final float DOOR_HEIGHT_STANDARD = 2.05f;
    public static final float DOOR_HEIGHT_TALL = 2.44949f;

    // Active WorldSession handle for on-demand lazy model resolution
    public volatile Object activeAc = null;
    public static Method mAcVisual = null;

    // PZ3D Shader program IDs
    private static Field fQl, fQi;

    // PZ3D Native Pipeline reflection handles
    private static Method mWallSurfaceFitted;
    private static Constructor<?> faceCtor;
    private static Method mBatches;
    private static Method mBatchTexture, mBatchVertices;

    // PZ3D ModelAsset reflection handles
    private static Class<?> modelAssetClass, placementClass;
    private static Constructor<?> placementCtor;
    private static Method mPlacementAsset, mPlacementTransform, mAssetPlace, mMeshOpaque, mMeshTextured;
    private static Method mModelCacheRetain, mModelCacheRelease;

    // ModelAsset.data reflection handles for texture extraction
    private static Field fAssetData;
    private static Method mDataParts;
    private static Method mPartPixels;
    private static Method mRasterWidth, mRasterHeight, mRasterRgba;

    // IsoDoor & IsoThumpable reflection handles
    public static Field fDoorOpenSprite, fDoorClosedSprite;
    public static Field fThumpOpenSprite, fThumpClosedSprite;

    private static boolean reflectionInit = false;

    // Texture cache for ModelAssets keyed per-texture / per-raster
    private static final Map<Object, Integer> modelGpuTextures = new IdentityHashMap<>();

    private long lastPruneTime = 0L;

    static {
        try {
            fQl = Renderer.class.getDeclaredField("ql");
            fQl.setAccessible(true);
            fQi = Renderer.class.getDeclaredField("qi");
            fQi.setAccessible(true);

            Class<?> wsClass = Class.forName("com.pavelvoronin.pz3d.WallSurface");
            mWallSurfaceFitted = wsClass.getDeclaredMethod("fitted", Texture.class, boolean.class);
            mWallSurfaceFitted.setAccessible(true);

            Class<?> faceClass = Class.forName("com.pavelvoronin.pz3d.Ground$Face");
            faceCtor = faceClass.getDeclaredConstructor(
                    float.class, float.class, float.class, boolean.class, float.class,
                    Texture.class, int.class, int.class, wsClass, int.class,
                    float.class, float.class, float.class, float.class
            );
            faceCtor.setAccessible(true);

            Class<?> groundClass = Class.forName("com.pavelvoronin.pz3d.Ground");
            mBatches = groundClass.getDeclaredMethod("batches", List.class, List.class, float.class, float.class, float.class);
            mBatches.setAccessible(true);

            Class<?> batchClass = Class.forName("com.pavelvoronin.pz3d.Ground$Batch");
            mBatchTexture = batchClass.getDeclaredMethod("texture");
            mBatchTexture.setAccessible(true);
            mBatchVertices = batchClass.getDeclaredMethod("vertices");
            mBatchVertices.setAccessible(true);

            modelAssetClass = Class.forName("com.pavelvoronin.pz3d.ModelAsset");
            placementClass = Class.forName("com.pavelvoronin.pz3d.ModelAsset$Placement");
            placementCtor = placementClass.getDeclaredConstructor(modelAssetClass, Matrix4fc.class);
            placementCtor.setAccessible(true);
            mPlacementAsset = placementClass.getDeclaredMethod("asset");
            mPlacementAsset.setAccessible(true);
            mPlacementTransform = placementClass.getDeclaredMethod("transform");
            mPlacementTransform.setAccessible(true);
            mAssetPlace = modelAssetClass.getDeclaredMethod("place", placementClass);
            mAssetPlace.setAccessible(true);

            Class<?> meshClass = Class.forName("com.pavelvoronin.pz3d.ModelAsset$Mesh");
            mMeshOpaque = meshClass.getDeclaredMethod("opaque");
            mMeshOpaque.setAccessible(true);
            mMeshTextured = meshClass.getDeclaredMethod("textured");
            mMeshTextured.setAccessible(true);

            Class<?> modelCacheClass = Class.forName("com.pavelvoronin.pz3d.ModelCache");
            mModelCacheRetain = modelCacheClass.getDeclaredMethod("retain", Object.class, Collection.class);
            mModelCacheRetain.setAccessible(true);
            mModelCacheRelease = modelCacheClass.getDeclaredMethod("release", Object.class);
            mModelCacheRelease.setAccessible(true);

            fAssetData = modelAssetClass.getDeclaredField("data");
            fAssetData.setAccessible(true);
            Class<?> dataClass = Class.forName("com.pavelvoronin.pz3d.ModelAsset$Data");
            mDataParts = dataClass.getDeclaredMethod("parts");
            mDataParts.setAccessible(true);
            Class<?> partClass = Class.forName("com.pavelvoronin.pz3d.ModelAsset$Part");
            mPartPixels = partClass.getDeclaredMethod("pixels");
            mPartPixels.setAccessible(true);
            Class<?> rasterClass = Class.forName("com.pavelvoronin.pz3d.ModelAsset$Raster");
            mRasterWidth = rasterClass.getDeclaredMethod("width");
            mRasterWidth.setAccessible(true);
            mRasterHeight = rasterClass.getDeclaredMethod("height");
            mRasterHeight.setAccessible(true);
            mRasterRgba = rasterClass.getDeclaredMethod("rgba");
            mRasterRgba.setAccessible(true);

            try {
                fDoorOpenSprite = IsoDoor.class.getDeclaredField("openSprite");
                fDoorOpenSprite.setAccessible(true);
                fDoorClosedSprite = IsoDoor.class.getDeclaredField("closedSprite");
                fDoorClosedSprite.setAccessible(true);
            } catch (Throwable ignored) {}

            try {
                fThumpOpenSprite = IsoThumpable.class.getDeclaredField("openSprite");
                fThumpOpenSprite.setAccessible(true);
                fThumpClosedSprite = IsoThumpable.class.getDeclaredField("closedSprite");
                fThumpClosedSprite.setAccessible(true);
            } catch (Throwable ignored) {}

            reflectionInit = true;
        } catch (Throwable t) {
            System.err.println("[PZ3DFix] InteractiveLayer reflection init warning: " + t.getMessage());
        }
    }

    public static int getProgramQl() {
        if (fQl != null) {
            try { return fQl.getInt(null); } catch (Throwable ignored) {}
        }
        return 0;
    }

    public static int getProgramQi() {
        if (fQi != null) {
            try { return fQi.getInt(null); } catch (Throwable ignored) {}
        }
        return 0;
    }

    public static Texture getTexture(IsoObject obj) {
        if (obj == null || obj.getSprite() == null) return null;
        Texture tex = null;
        if (obj.getDir() != null) {
            tex = obj.getSprite().getTextureForCurrentFrame(obj.getDir(), obj);
        }
        if (tex == null || !tex.isReady()) {
            tex = obj.getSprite().getTextureForCurrentFrame(IsoDirections.N, obj);
        }
        if (tex == null || !tex.isReady()) {
            tex = obj.getSprite().getTextureForCurrentFrame(IsoDirections.W, obj);
        }
        if (tex == null || !tex.isReady()) {
            tex = obj.getSprite().getTextureForCurrentFrame(IsoDirections.S, obj);
        }
        if (tex == null || !tex.isReady()) {
            tex = obj.getSprite().getTextureForCurrentFrame(IsoDirections.E, obj);
        }
        return (tex != null && tex.isReady()) ? tex : null;
    }

    public static Texture getClosedDoorTexture(IsoObject door) {
        if (door == null) return null;
        if (door instanceof IsoDoor && fDoorClosedSprite != null) {
            try {
                IsoSprite spr = (IsoSprite) fDoorClosedSprite.get(door);
                if (spr != null) {
                    Texture tex = spr.getTextureForCurrentFrame(IsoDirections.N, door);
                    if (tex != null && tex.isReady()) return tex;
                }
            } catch (Throwable ignored) {}
        }
        if (door instanceof IsoThumpable && fThumpClosedSprite != null) {
            try {
                IsoSprite spr = (IsoSprite) fThumpClosedSprite.get(door);
                if (spr != null) {
                    Texture tex = spr.getTextureForCurrentFrame(IsoDirections.N, door);
                    if (tex != null && tex.isReady()) return tex;
                }
            } catch (Throwable ignored) {}
        }
        return getTexture(door);
    }

    public static Object resolveVisualPlacement(Object ac, Method mAcVisual, String sprName, IsoObject obj,
                                                float x, float y, float z, float rot, String... states) {
        if (ac == null || mAcVisual == null || sprName == null) return null;
        for (String state : states) {
            try {
                List<Object> temp = new ArrayList<>(1);
                boolean found = (boolean) mAcVisual.invoke(ac, sprName, state, obj, x, y, z, rot, temp);
                if (found && !temp.isEmpty()) {
                    return temp.get(0);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static Object createHingeSwungPlacement(Object placementClosed, float hingeX, float hingeY, boolean north, boolean hingeEast, boolean hingeSouth, boolean reverseSwing) {
        if (!reflectionInit || placementClosed == null || mPlacementAsset == null || mPlacementTransform == null || placementCtor == null) {
            return null;
        }
        try {
            Object asset = mPlacementAsset.invoke(placementClosed);
            Matrix4fc closedMat = (Matrix4fc) mPlacementTransform.invoke(placementClosed);

            float baseAngle;
            if (north) {
                baseAngle = hingeEast ? -90.0f : 90.0f;
            } else {
                baseAngle = hingeSouth ? 90.0f : -90.0f;
            }
            float swingAngle = reverseSwing ? -baseAngle : baseAngle;

            Matrix4f openMat = new Matrix4f()
                    .translation(hingeX, hingeY, 0.0f)
                    .rotateZ((float) Math.toRadians(swingAngle))
                    .translate(-hingeX, -hingeY, 0.0f)
                    .mul(closedMat);

            return placementCtor.newInstance(asset, openMat);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object createHingeSwungPlacement(Object placementClosed, float hingeX, float hingeY, boolean north, boolean hingeEast) {
        return createHingeSwungPlacement(placementClosed, hingeX, hingeY, north, hingeEast, false, false);
    }

    public static Object createHingeSwungPlacement(Object placementClosed, float hingeX, float hingeY, boolean north, boolean hingeEast, boolean reverseSwing) {
        return createHingeSwungPlacement(placementClosed, hingeX, hingeY, north, hingeEast, false, reverseSwing);
    }

    public static int ensureModelTextureUploaded(Object modelAsset, Texture fallbackTex) {
        if (fallbackTex != null && fallbackTex.isReady() && fallbackTex.getID() > 0) {
            return fallbackTex.getID();
        }
        if (fallbackTex == null) return 0;

        Integer cached = modelGpuTextures.get(fallbackTex);
        if (cached != null && cached > 0) {
            return cached;
        }

        if (!reflectionInit || modelAsset == null || fAssetData == null) {
            return 0;
        }

        try {
            Object data = fAssetData.get(modelAsset);
            if (data == null) return 0;
            List<?> parts = (List<?>) mDataParts.invoke(data);
            if (parts == null || parts.isEmpty()) return 0;

            for (Object part : parts) {
                Object raster = mPartPixels.invoke(part);
                if (raster == null) continue;

                Integer rCached = modelGpuTextures.get(raster);
                if (rCached != null && rCached > 0) {
                    modelGpuTextures.put(fallbackTex, rCached);
                    return rCached;
                }

                int width = (int) mRasterWidth.invoke(raster);
                int height = (int) mRasterHeight.invoke(raster);
                byte[] rgba = (byte[]) mRasterRgba.invoke(raster);

                if (width <= 0 || height <= 0 || rgba == null || rgba.length == 0) continue;

                ByteBuffer buf = BufferUtils.createByteBuffer(rgba.length);
                buf.put(rgba).flip();

                int texId = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

                modelGpuTextures.put(raster, texId);
                modelGpuTextures.put(fallbackTex, texId);
                return texId;
            }
        } catch (Throwable ignored) {}

        return 0;
    }

    public static final class StateMesh {
        public float[] opaqueVerts = null;
        public float[] texturedVerts = null;
        public Texture texture = null;
    }

    public static final class DoorInstance {
        public final IsoObject doorObj;
        public final float x, y, z;
        public final boolean north;
        public final boolean wide, garage;
        public final boolean isDoubleDoor, isSecondLeaf;
        public final boolean hingeEast, hingeSouth;
        public final boolean hasNeighborNeg, hasNeighborPos;
        public final Texture texClosed;

        public boolean open = false;
        public boolean destroyed = false;

        public Object placementClosed = null;
        public Object placementOpen = null;

        public StateMesh closedMesh = null;
        public StateMesh openMesh = null;

        public DoorInstance(IsoObject doorObj, float x, float y, float z, boolean north, boolean wide, boolean garage,
                            boolean isDoubleDoor, boolean isSecondLeaf, boolean hingeEast, boolean hingeSouth,
                            boolean hasNeighborNeg, boolean hasNeighborPos, Texture texClosed) {
            this.doorObj = doorObj;
            this.x = x;
            this.y = y;
            this.z = z;
            this.north = north;
            this.wide = wide;
            this.garage = garage;
            this.isDoubleDoor = isDoubleDoor;
            this.isSecondLeaf = isSecondLeaf;
            this.hingeEast = hingeEast;
            this.hingeSouth = hingeSouth;
            this.hasNeighborNeg = hasNeighborNeg;
            this.hasNeighborPos = hasNeighborPos;
            this.texClosed = texClosed;
            updateState();
        }

        public void updateState() {
            if (doorObj == null) return;
            this.open = (doorObj instanceof IsoDoor) ? ((IsoDoor) doorObj).IsOpen() : ((IsoThumpable) doorObj).IsOpen();
            this.destroyed = (doorObj instanceof IsoDoor) ? ((IsoDoor) doorObj).isDestroyed() : ((IsoThumpable) doorObj).isDestroyed();
        }
    }

    public static final class WindowInstance {
        public final IsoWindow windowObj;
        public final float x, y, z;
        public final boolean north;
        public final List<Geometry.Box> frameBoxes;

        public boolean open = false;
        public boolean smashed = false;
        public boolean glassRemoved = false;

        public Object placementIntact = null;
        public Object placementSmashed = null;

        public StateMesh frameMesh = null;

        public WindowInstance(IsoWindow windowObj, float x, float y, float z, boolean north,
                              List<Geometry.Box> frameBoxes) {
            this.windowObj = windowObj;
            this.x = x;
            this.y = y;
            this.z = z;
            this.north = north;
            this.frameBoxes = (frameBoxes != null) ? List.copyOf(frameBoxes) : Collections.emptyList();
            updateState();
        }

        public void updateState() {
            if (windowObj == null) return;
            this.open = windowObj.IsOpen();
            this.smashed = windowObj.isSmashed();
            this.glassRemoved = windowObj.isGlassRemoved();
        }
    }

    public static final class CurtainInstance {
        public final IsoObject curtainObj;
        public final float x, y, z;
        public final boolean north;
        public final int side;

        public boolean open = false;
        public boolean hasSheet = true;
        public StateMesh mesh = null;

        public CurtainInstance(IsoObject curtainObj, float x, float y, float z, boolean north, int side) {
            this.curtainObj = curtainObj;
            this.x = x;
            this.y = y;
            this.z = z;
            this.north = north;
            this.side = side;
            updateState();
        }

        public void updateState() {
            if (curtainObj == null) return;
            if (curtainObj instanceof IsoCurtain) {
                this.open = ((IsoCurtain) curtainObj).IsOpen();
                this.hasSheet = true;
            } else if (curtainObj instanceof IsoDoor) {
                IsoDoor door = (IsoDoor) curtainObj;
                this.hasSheet = (door.HasCurtains() != null);
                this.open = door.isCurtainOpen();
            }
        }
    }

    private final Map<IsoObject, DoorInstance> doors = new ConcurrentHashMap<>();
    private final Map<IsoWindow, WindowInstance> windows = new ConcurrentHashMap<>();
    private final Map<IsoObject, CurtainInstance> curtains = new ConcurrentHashMap<>();

    private int vaoOpaque = 0;
    private int vboOpaque = 0;
    private int vaoTextured = 0;
    private int vboTextured = 0;

    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    public void pruneDistant(int centerChunkX, int centerChunkY, int maxChunkRadius) {
        long now = System.currentTimeMillis();
        if (now - lastPruneTime < 2000L) return;
        lastPruneTime = now;

        doors.values().removeIf(d -> {
            int cx = Math.floorDiv((int) d.x, 8);
            int cy = Math.floorDiv((int) d.y, 8);
            return Math.abs(cx - centerChunkX) > maxChunkRadius || Math.abs(cy - centerChunkY) > maxChunkRadius;
        });

        windows.values().removeIf(w -> {
            int cx = Math.floorDiv((int) w.x, 8);
            int cy = Math.floorDiv((int) w.y, 8);
            return Math.abs(cx - centerChunkX) > maxChunkRadius || Math.abs(cy - centerChunkY) > maxChunkRadius;
        });

        curtains.values().removeIf(c -> {
            int cx = Math.floorDiv((int) c.x, 8);
            int cy = Math.floorDiv((int) c.y, 8);
            return Math.abs(cx - centerChunkX) > maxChunkRadius || Math.abs(cy - centerChunkY) > maxChunkRadius;
        });

        retainActiveModels();
    }

    public void registerDoor(IsoObject door, float x, float y, float z, boolean north, boolean wide, boolean garage,
                             boolean isDoubleDoor, boolean isSecondLeaf, boolean hingeEast, boolean hingeSouth,
                             boolean hasNeighborNeg, boolean hasNeighborPos,
                             Object placementClosed, Object placementOpen, Texture texClosed) {
        if (door == null) return;
        doors.compute(door, (k, existing) -> {
            if (existing != null) {
                if (placementClosed != null) existing.placementClosed = placementClosed;
                if (placementOpen != null) existing.placementOpen = placementOpen;
                existing.updateState();
                return existing;
            }
            DoorInstance inst = new DoorInstance(door, x, y, z, north, wide, garage, isDoubleDoor, isSecondLeaf,
                    hingeEast, hingeSouth, hasNeighborNeg, hasNeighborPos, texClosed);
            inst.placementClosed = placementClosed;
            inst.placementOpen = placementOpen;
            return inst;
        });
        retainActiveModels();
    }

    public void registerWindow(IsoWindow window, float x, float y, float z, boolean north,
                               Object placementIntact, Object placementSmashed, Texture texFallback,
                               List<Geometry.Box> frameBoxes) {
        if (window == null) return;
        windows.compute(window, (k, existing) -> {
            if (existing != null) {
                if (placementIntact != null) existing.placementIntact = placementIntact;
                if (placementSmashed != null) existing.placementSmashed = placementSmashed;
                existing.updateState();
                return existing;
            }
            WindowInstance inst = new WindowInstance(window, x, y, z, north, frameBoxes);
            inst.placementIntact = placementIntact;
            inst.placementSmashed = placementSmashed;
            return inst;
        });
        retainActiveModels();
    }

    public void registerCurtain(IsoObject curtain, float x, float y, float z, boolean north, int side,
                                Object placementClosed, Object placementOpen, Texture texFallback) {
        if (curtain == null) return;
        curtains.compute(curtain, (k, existing) -> {
            if (existing != null) {
                existing.updateState();
                return existing;
            }
            return new CurtainInstance(curtain, x, y, z, north, side);
        });
    }

    public void onDoorToggled(IsoObject door) {
        DoorInstance inst = doors.get(door);
        if (inst != null) {
            inst.updateState();
        }
    }

    public void onWindowChanged(IsoWindow window) {
        WindowInstance inst = windows.get(window);
        if (inst != null) {
            inst.updateState();
            inst.frameMesh = null;
        }
    }

    public void onCurtainToggled(IsoObject curtain) {
        CurtainInstance inst = curtains.get(curtain);
        if (inst != null) {
            inst.updateState();
            inst.mesh = null;
        }
    }

    public void removeObject(IsoObject obj) {
        if (obj == null) return;
        doors.remove(obj);
        if (obj instanceof IsoWindow) windows.remove(obj);
        curtains.remove(obj);
        retainActiveModels();
    }

    public void retainActiveModels() {
        if (mModelCacheRetain == null || mPlacementAsset == null) return;
        ArrayList<Object> list = new ArrayList<>(32);
        try {
            for (DoorInstance d : doors.values()) {
                if (d.placementClosed != null) list.add(mPlacementAsset.invoke(d.placementClosed));
                if (d.placementOpen != null) list.add(mPlacementAsset.invoke(d.placementOpen));
            }
            for (WindowInstance w : windows.values()) {
                if (w.placementIntact != null) list.add(mPlacementAsset.invoke(w.placementIntact));
                if (w.placementSmashed != null) list.add(mPlacementAsset.invoke(w.placementSmashed));
            }
            mModelCacheRetain.invoke(null, this, list);
        } catch (Throwable ignored) {}
    }

    public void injectDynamicCollisions(List<Geometry.Box> list, float sceneOx, float sceneOy) {
        if (list == null || doors.isEmpty()) return;

        IsoPlayer player = IsoPlayer.getInstance();
        float px = (player != null) ? player.getX() : sceneOx;
        float py = (player != null) ? player.getY() : sceneOy;

        for (DoorInstance d : doors.values()) {
            if (Math.abs(d.x - px) > 6.0f || Math.abs(d.y - py) > 6.0f) {
                continue;
            }

            boolean isOpen = (d.doorObj instanceof IsoDoor) ? ((IsoDoor) doorObj(d.doorObj)).IsOpen() : ((IsoThumpable) d.doorObj).IsOpen();
            d.open = isOpen;
            if (!d.destroyed && !isOpen) {
                float fHeight = (d.garage || d.wide) ? DOOR_HEIGHT_TALL : DOOR_HEIGHT_STANDARD;
                float cU0 = (d.garage || d.wide || d.hasNeighborNeg) ? 0.0f : 0.05f;
                float cU1 = (d.garage || d.wide || d.hasNeighborPos) ? 1.0f : 0.95f;
                Geometry.Box box = createPlane(d.x, d.y, d.z, d.north, cU0, cU1, 0.0f, fHeight, DOOR_THICKNESS, false);
                list.add(box);
            }
        }
    }

    private static IsoDoor doorObj(IsoObject obj) {
        return (IsoDoor) obj;
    }

    public void renderOpaque(Matrix4f viewProjection, float eyeX, float eyeY, float eyeZ) {
        if (doors.isEmpty() && windows.isEmpty() && curtains.isEmpty()) return;

        float sceneOx = Patch_PZ3D_AtmosphereUpload.hasSceneOrigin ? Patch_PZ3D_AtmosphereUpload.sceneOx : 0.0f;
        float sceneOy = Patch_PZ3D_AtmosphereUpload.hasSceneOrigin ? Patch_PZ3D_AtmosphereUpload.sceneOy : 0.0f;
        float sceneOz = Patch_PZ3D_AtmosphereUpload.hasSceneOrigin ? Patch_PZ3D_AtmosphereUpload.sceneOz : 0.0f;

        int camChunkX = Math.floorDiv((int) (eyeX + sceneOx), 8);
        int camChunkY = Math.floorDiv((int) (eyeY + sceneOy), 8);
        pruneDistant(camChunkX, camChunkY, Patch_PZ3D_ToggleSpeedup.MAX_NEAR_CHUNK_RADIUS);

        initBuffers();

        int qi = getProgramQi();
        int ql = getProgramQl();

        GL11.glPushAttrib(GL11.GL_POLYGON_BIT | GL11.GL_ENABLE_BIT);
        GL11.glFrontFace(2304);
        GL11.glDisable(GL11.GL_CULL_FACE);

        try {
            if (reflectionInit) {
                for (DoorInstance d : doors.values()) {
                    if (d.destroyed) continue;
                    d.updateState();

                    if (d.doorObj.getSprite() != null && activeAc != null && mAcVisual != null) {
                        String sprName = d.doorObj.getSprite().getName();
                        float rot = d.north ? 0.0f : 90.0f;

                        if (d.placementClosed == null) {
                            d.placementClosed = resolveVisualPlacement(activeAc, mAcVisual, sprName, d.doorObj,
                                    d.x, d.y, d.z, rot, "closed", "default");
                        }
                        if (d.placementOpen == null) {
                            d.placementOpen = resolveVisualPlacement(activeAc, mAcVisual, sprName, d.doorObj,
                                    d.x, d.y, d.z, rot, "open");
                            if (d.placementOpen == null && d.placementClosed != null) {
                                float hingeX = d.isDoubleDoor ? (d.north ? (d.isSecondLeaf ? d.x + 1.0f : d.x) : d.x) : (d.x + (d.hingeEast ? 1.0f : 0.0f));
                                float hingeY = d.isDoubleDoor ? (d.north ? d.y : (d.isSecondLeaf ? d.y + 1.0f : d.y)) : (d.y + (d.hingeSouth ? 1.0f : 0.0f));
                                d.placementOpen = createHingeSwungPlacement(d.placementClosed, hingeX, hingeY, d.north, d.hingeEast, d.hingeSouth, d.isSecondLeaf);
                            }
                        }
                    }

                    Object placement = d.open ? d.placementOpen : d.placementClosed;
                    if (placement == null) continue;
                    try {
                        Object asset = mPlacementAsset.invoke(placement);
                        Matrix4fc world = (Matrix4fc) mPlacementTransform.invoke(placement);
                        drawPlacementMesh(asset, world, viewProjection, sceneOx, sceneOy, sceneOz, eyeX, eyeY, eyeZ, qi, ql);
                    } catch (Throwable ignored) {}
                }

                for (WindowInstance w : windows.values()) {
                    w.updateState();

                    if (w.windowObj.getSprite() != null && activeAc != null && mAcVisual != null) {
                        String sprName = w.windowObj.getSprite().getName();
                        float rot = w.north ? 0.0f : 90.0f;

                        if (w.placementIntact == null) {
                            w.placementIntact = resolveVisualPlacement(activeAc, mAcVisual, sprName, w.windowObj,
                                    w.x, w.y, w.z, rot, "closed", "default");
                        }
                        if (w.placementSmashed == null) {
                            w.placementSmashed = resolveVisualPlacement(activeAc, mAcVisual, sprName, w.windowObj,
                                    w.x, w.y, w.z, rot, "smashed", "open");
                        }
                    }

                    Object placement = w.smashed ? w.placementSmashed : w.placementIntact;
                    if (placement != null) {
                        try {
                            Object asset = mPlacementAsset.invoke(placement);
                            Matrix4fc world = (Matrix4fc) mPlacementTransform.invoke(placement);
                            drawPlacementMesh(asset, world, viewProjection, sceneOx, sceneOy, sceneOz, eyeX, eyeY, eyeZ, qi, ql);
                        } catch (Throwable ignored) {}
                    }
                }
            }

            for (WindowInstance w : windows.values()) {
                if (!w.smashed && w.placementIntact != null) continue;
                if (w.smashed && w.placementSmashed != null) continue;

                w.updateState();
                StateMesh m = getOrCreateWindowMesh(w);
                if (m == null || qi <= 0) continue;

                if (m.opaqueVerts != null && m.opaqueVerts.length > 0) {
                    GL20.glUseProgram(qi);
                    uploadMatrix(qi, "viewProjection", viewProjection);
                    GL20.glUniform3f(GL20.glGetUniformLocation(qi, "eye"), eyeX, eyeY, eyeZ);
                    GL20.glUniform3f(GL20.glGetUniformLocation(qi, "meshOrigin"), sceneOx, sceneOy, sceneOz);

                    GL30.glBindVertexArray(vaoOpaque);
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboOpaque);
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, m.opaqueVerts, GL15.GL_DYNAMIC_DRAW);

                    GL20.glDisableVertexAttribArray(3);
                    GL20.glVertexAttrib4f(3, 0.0f, 0.0f, 0.0f, 0.0f);

                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, m.opaqueVerts.length / 9);
                    GL30.glBindVertexArray(0);
                }
            }

            for (DoorInstance d : doors.values()) {
                if (d.destroyed) continue;
                d.updateState();
                Object activePlacement = d.open ? d.placementOpen : d.placementClosed;
                if (activePlacement != null) continue;

                StateMesh m = getOrCreateDoorMesh(d);
                if (m == null || m.texturedVerts == null || m.texturedVerts.length == 0 || m.texture == null || !m.texture.isReady() || ql <= 0) {
                    continue;
                }

                GL20.glUseProgram(ql);
                uploadMatrix(ql, "viewProjection", viewProjection);
                GL20.glUniform3f(GL20.glGetUniformLocation(ql, "eye"), eyeX, eyeY, eyeZ);
                GL20.glUniform3f(GL20.glGetUniformLocation(ql, "meshOrigin"), sceneOx, sceneOy, sceneOz);
                GL20.glUniform3f(GL20.glGetUniformLocation(ql, "objectTint"), 1.0f, 1.0f, 1.0f);
                GL20.glUniform1i(GL20.glGetUniformLocation(ql, "textured"), 1);
                GL20.glUniform1i(GL20.glGetUniformLocation(ql, "tex"), 0);

                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, m.texture.getID());

                GL30.glBindVertexArray(vaoTextured);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboTextured);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, m.texturedVerts, GL15.GL_DYNAMIC_DRAW);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, m.texturedVerts.length / 10);
                GL30.glBindVertexArray(0);
            }

            for (CurtainInstance c : curtains.values()) {
                c.updateState();
                if (!c.hasSheet) continue;

                StateMesh m = getOrCreateCurtainMesh(c);
                if (m == null || m.texturedVerts == null || m.texturedVerts.length == 0 || m.texture == null || !m.texture.isReady() || ql <= 0) {
                    continue;
                }

                GL20.glUseProgram(ql);
                uploadMatrix(ql, "viewProjection", viewProjection);
                GL20.glUniform3f(GL20.glGetUniformLocation(ql, "eye"), eyeX, eyeY, eyeZ);
                GL20.glUniform3f(GL20.glGetUniformLocation(ql, "meshOrigin"), sceneOx, sceneOy, sceneOz);
                GL20.glUniform3f(GL20.glGetUniformLocation(ql, "objectTint"), 1.0f, 1.0f, 1.0f);
                GL20.glUniform1i(GL20.glGetUniformLocation(ql, "textured"), 1);
                GL20.glUniform1i(GL20.glGetUniformLocation(ql, "tex"), 0);

                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, m.texture.getID());

                GL30.glBindVertexArray(vaoTextured);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboTextured);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, m.texturedVerts, GL15.GL_DYNAMIC_DRAW);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, m.texturedVerts.length / 10);
                GL30.glBindVertexArray(0);
            }
        } finally {
            GL11.glPopAttrib();
            GL20.glUseProgram(0);
        }
    }

    public void renderTransparent(Matrix4f viewProjection, float eyeX, float eyeY, float eyeZ) {
        if (windows.isEmpty()) return;

        int ql = getProgramQl();
        if (ql <= 0) return;

        float sceneOx = Patch_PZ3D_AtmosphereUpload.hasSceneOrigin ? Patch_PZ3D_AtmosphereUpload.sceneOx : 0.0f;
        float sceneOy = Patch_PZ3D_AtmosphereUpload.hasSceneOrigin ? Patch_PZ3D_AtmosphereUpload.sceneOy : 0.0f;
        float sceneOz = Patch_PZ3D_AtmosphereUpload.hasSceneOrigin ? Patch_PZ3D_AtmosphereUpload.sceneOz : 0.0f;

        initBuffers();

        GL11.glPushAttrib(GL11.GL_POLYGON_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);

        try {
            GL20.glUseProgram(ql);
            uploadMatrix(ql, "viewProjection", viewProjection);
            GL20.glUniform3f(GL20.glGetUniformLocation(ql, "eye"), eyeX, eyeY, eyeZ);
            GL20.glUniform3f(GL20.glGetUniformLocation(ql, "meshOrigin"), sceneOx, sceneOy, sceneOz);
            GL20.glUniform3f(GL20.glGetUniformLocation(ql, "objectTint"), 1.0f, 1.0f, 1.0f);
            GL20.glUniform1i(GL20.glGetUniformLocation(ql, "textured"), 1);
            GL20.glUniform1i(GL20.glGetUniformLocation(ql, "tex"), 0);

            GL30.glBindVertexArray(vaoTextured);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboTextured);

            for (WindowInstance w : windows.values()) {
                if (w.smashed || w.glassRemoved) continue;
                if (w.placementIntact != null) continue;

                Texture tex = getTexture(w.windowObj);
                if (tex == null || !tex.isReady()) continue;

                float left = 0.095f, right = 0.905f, bottom = 0.845f, top = 2.055f;
                float halfZ = (bottom + top) * 0.5f;
                float frameThick = 0.027f;

                float gBottom = w.open ? halfZ : (bottom + frameThick);
                float gTop = top - frameThick;

                float gx0 = w.x + (w.north ? (left + frameThick) : 0.0f);
                float gy0 = w.y + (w.north ? 0.0f : (left + frameThick));
                float gx1 = w.x + (w.north ? (right - frameThick) : 0.0f);
                float gy1 = w.y + (w.north ? 0.0f : (right - frameThick));

                float[] glassData = new float[6 * 10];
                float[] dummyUv = new float[]{tex.getXStart(), tex.getYEnd(), tex.getXEnd(), tex.getYEnd(), tex.getXEnd(), tex.getYStart(), tex.getXStart(), tex.getYStart()};
                putFittedQuad(glassData, 0, gx0, gy0, w.z + gBottom, gx1, gy1, w.z + gBottom, gx1, gy1, w.z + gTop, gx0, gy0, w.z + gTop, dummyUv, tex, 34.0f);

                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex.getID());

                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, glassData, GL15.GL_DYNAMIC_DRAW);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            }
        } finally {
            GL30.glBindVertexArray(0);
            GL20.glUseProgram(0);
            GL11.glPopAttrib();
        }
    }

    private StateMesh getOrCreateDoorMesh(DoorInstance d) {
        if (d.open && d.openMesh != null) return d.openMesh;
        if (!d.open && d.closedMesh != null) return d.closedMesh;

        Texture tex = (d.texClosed != null && d.texClosed.isReady()) ? d.texClosed : getClosedDoorTexture(d.doorObj);
        if (tex == null || !tex.isReady()) {
            tex = getTexture(d.doorObj);
        }
        if (tex == null || !tex.isReady()) return null;

        float fHeight = (d.garage || d.wide) ? DOOR_HEIGHT_TALL : DOOR_HEIGHT_STANDARD;

        float u0 = (d.garage || d.wide || d.hasNeighborNeg) ? 0.0f : 0.1f;
        float u1 = (d.garage || d.wide || d.hasNeighborPos) ? 1.0f : 0.9f;

        StateMesh mesh = new StateMesh();
        mesh.texture = tex;

        try {
            if (faceCtor != null && mBatches != null && mWallSurfaceFitted != null) {
                Object wallSurface = mWallSurfaceFitted.invoke(null, tex, d.north);
                float offset = 0.037f;

                Object faceFront = faceCtor.newInstance(d.x, d.y, d.z, d.north, offset, tex, 3, 1, wallSurface, 1, u0, u1, 0.0f, fHeight);
                Object faceBack = faceCtor.newInstance(d.x, d.y, d.z, d.north, -offset, tex, 3, 1, wallSurface, -1, u0, u1, 0.0f, fHeight);

                List<?> batches = (List<?>) mBatches.invoke(null, Collections.emptyList(), List.of(faceFront, faceBack), 0.0f, 0.0f, 0.0f);
                if (batches != null && !batches.isEmpty()) {
                    Object b0 = batches.get(0);
                    float[] verts = (float[]) mBatchVertices.invoke(b0);
                    mesh.texture = (Texture) mBatchTexture.invoke(b0);

                    if (d.open && verts != null) {
                        float hX, hY;
                        float swingAngle;

                        if (d.isDoubleDoor) {
                            if (d.north) {
                                hX = d.isSecondLeaf ? (d.x + 1.0f) : d.x;
                                hY = d.y;
                                swingAngle = d.isSecondLeaf ? -90.0f : 90.0f;
                            } else {
                                hX = d.x;
                                hY = d.isSecondLeaf ? (d.y + 1.0f) : d.y;
                                swingAngle = d.isSecondLeaf ? 90.0f : -90.0f;
                            }
                        } else {
                            hX = d.x + (d.hingeEast ? 1.0f : 0.0f);
                            hY = d.y + (d.hingeSouth ? 1.0f : 0.0f);
                            swingAngle = d.north ? (d.hingeEast ? -90.0f : 90.0f) : (d.hingeSouth ? 90.0f : -90.0f);
                        }

                        double rad = Math.toRadians(swingAngle);
                        float cosA = (float) Math.cos(rad);
                        float sinA = (float) Math.sin(rad);

                        for (int i = 0; i < verts.length; i += 10) {
                            float vx = verts[i] - hX;
                            float vy = verts[i + 1] - hY;
                            verts[i] = hX + (vx * cosA - vy * sinA);
                            verts[i + 1] = hY + (vx * sinA + vy * cosA);
                        }
                    }

                    mesh.texturedVerts = verts;
                }
            }
        } catch (Throwable t) {
            System.err.println("[PZ3DFix] Error building procedural door mesh: " + t.getMessage());
        }

        if (d.open) d.openMesh = mesh;
        else d.closedMesh = mesh;
        return mesh;
    }

    private StateMesh getOrCreateWindowMesh(WindowInstance w) {
        if (w.frameMesh != null) return w.frameMesh;

        StateMesh mesh = new StateMesh();
        mesh.texture = getTexture(w.windowObj);

        if (w.frameBoxes != null && !w.frameBoxes.isEmpty()) {
            mesh.opaqueVerts = Geometry.vertices(w.frameBoxes);
        }

        w.frameMesh = mesh;
        return mesh;
    }

    private StateMesh getOrCreateCurtainMesh(CurtainInstance c) {
        if (c.mesh != null) return c.mesh;

        Texture tex = getTexture(c.curtainObj);
        if (tex == null || !tex.isReady()) return null;

        StateMesh mesh = new StateMesh();
        mesh.texture = tex;

        float width = c.open ? 0.32f : 0.84f;
        float zBottom = 0.75f;
        float zTop = 1.95f;
        float off = c.side * 0.055f;

        if (faceCtor != null && mBatches != null && mWallSurfaceFitted != null) {
            try {
                Object wallSurface = mWallSurfaceFitted.invoke(null, tex, c.north);
                Object faceFront = faceCtor.newInstance(c.x, c.y, c.z, c.north, off, tex, 2, 1, wallSurface, c.side, 0.16f, 0.16f + width, zBottom, zTop);
                Object faceBack = faceCtor.newInstance(c.x, c.y, c.z, c.north, off - (c.side * 0.001f), tex, 2, 1, wallSurface, -c.side, 0.16f, 0.16f + width, zBottom, zTop);

                List<?> batches = (List<?>) mBatches.invoke(null, Collections.emptyList(), List.of(faceFront, faceBack), 0.0f, 0.0f, 0.0f);
                if (batches != null && !batches.isEmpty()) {
                    Object b0 = batches.get(0);
                    mesh.texturedVerts = (float[]) mBatchVertices.invoke(b0);
                    mesh.texture = (Texture) mBatchTexture.invoke(b0);
                }
            } catch (Throwable t) {
                System.err.println("[PZ3DFix] Error building curtain mesh: " + t.getMessage());
            }
        }

        c.mesh = mesh;
        return mesh;
    }

    private void drawPlacementMesh(Object modelAsset, Matrix4fc worldMatrix, Matrix4f vp,
                                   float sceneOx, float sceneOy, float sceneOz,
                                   float eyeX, float eyeY, float eyeZ,
                                   int progQi, int progQl) {
        if (!reflectionInit || modelAsset == null || placementCtor == null) return;
        try {
            Object placement = placementCtor.newInstance(modelAsset, worldMatrix);
            Object mesh = mAssetPlace.invoke(modelAsset, placement);
            if (mesh == null) return;

            if (progQi > 0) {
                float[] opaque = (float[]) mMeshOpaque.invoke(mesh);
                if (opaque != null && opaque.length > 0) {
                    GL20.glUseProgram(progQi);
                    uploadMatrix(progQi, "viewProjection", vp);
                    GL20.glUniform3f(GL20.glGetUniformLocation(progQi, "eye"), eyeX, eyeY, eyeZ);
                    GL20.glUniform3f(GL20.glGetUniformLocation(progQi, "meshOrigin"), sceneOx, sceneOy, sceneOz);

                    GL30.glBindVertexArray(vaoOpaque);
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboOpaque);
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, opaque, GL15.GL_DYNAMIC_DRAW);

                    GL20.glDisableVertexAttribArray(3);
                    GL20.glVertexAttrib4f(3, 0.0f, 0.0f, 0.0f, 0.0f);

                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, opaque.length / 9);
                    GL30.glBindVertexArray(0);
                }
            }

            if (progQl > 0) {
                List<?> batches = (List<?>) mMeshTextured.invoke(mesh);
                if (batches != null && !batches.isEmpty()) {
                    GL20.glUseProgram(progQl);
                    uploadMatrix(progQl, "viewProjection", vp);
                    GL20.glUniform3f(GL20.glGetUniformLocation(progQl, "eye"), eyeX, eyeY, eyeZ);
                    GL20.glUniform3f(GL20.glGetUniformLocation(progQl, "meshOrigin"), sceneOx, sceneOy, sceneOz);
                    GL20.glUniform3f(GL20.glGetUniformLocation(progQl, "objectTint"), 1.0f, 1.0f, 1.0f);
                    GL20.glUniform1i(GL20.glGetUniformLocation(progQl, "textured"), 1);
                    GL20.glUniform1i(GL20.glGetUniformLocation(progQl, "tex"), 0);

                    GL30.glBindVertexArray(vaoTextured);
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboTextured);

                    for (Object batch : batches) {
                        Texture tex = (Texture) mBatchTexture.invoke(batch);
                        float[] verts = (float[]) mBatchVertices.invoke(batch);
                        if (verts == null || verts.length == 0) continue;

                        int bindTexId = ensureModelTextureUploaded(modelAsset, tex);
                        if (bindTexId <= 0) continue;

                        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_DYNAMIC_DRAW);
                        GL13.glActiveTexture(GL13.GL_TEXTURE0);
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bindTexId);
                        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verts.length / 10);
                    }
                    GL30.glBindVertexArray(0);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void uploadMatrix(int program, String name, Matrix4f mat) {
        matrixBuffer.clear();
        mat.get(matrixBuffer);
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc >= 0) GL20.glUniformMatrix4fv(loc, false, matrixBuffer);
    }

    public static Geometry.Box createPlane(float f, float f2, float f3, boolean z, float f4, float f5, float f6, float f7, float f8, boolean z2) {
        return z
                ? new Geometry.Box(f + f4, f2 - f8, f3 + f6, f + f5, f2 + f8, f3 + f7, 0.58f, 0.57f, 0.54f, true, z2)
                : new Geometry.Box(f - f8, f2 + f4, f3 + f6, f + f8, f2 + f5, f3 + f7, 0.58f, 0.57f, 0.54f, true, z2);
    }

    private static void putFittedQuad(float[] data, int off,
                                      float x0, float y0, float z0,
                                      float x1, float y1, float z1,
                                      float x2, float y2, float z2,
                                      float x3, float y3, float z3,
                                      float[] uv, Texture tex, float kind) {
        float cx0 = tex.getXStart(), cy0 = tex.getYStart();
        float cx1 = tex.getXEnd(),   cy1 = tex.getYEnd();

        float u0 = Math.max(cx0, Math.min(cx1, uv[0])), v0 = Math.max(cy0, Math.min(cy1, uv[1]));
        float u1 = Math.max(cx0, Math.min(cx1, uv[2])), v1 = Math.max(cy0, Math.min(cy1, uv[3]));
        float u2 = Math.max(cx0, Math.min(cx1, uv[4])), v2 = Math.max(cy0, Math.min(cy1, uv[5]));
        float u3 = Math.max(cx0, Math.min(cx1, uv[6])), v3 = Math.max(cy0, Math.min(cy1, uv[7]));

        putV(data, off,      x0, y0, z0, u0, v0, cx0, cy0, cx1, cy1, kind);
        putV(data, off + 10, x1, y1, z1, u1, v1, cx0, cy0, cx1, cy1, kind);
        putV(data, off + 20, x2, y2, z2, u2, v2, cx0, cy0, cx1, cy1, kind);

        putV(data, off + 30, x0, y0, z0, u0, v0, cx0, cy0, cx1, cy1, kind);
        putV(data, off + 40, x2, y2, z2, u2, v2, cx0, cy0, cx1, cy1, kind);
        putV(data, off + 50, x3, y3, z3, u3, v3, cx0, cy0, cx1, cy1, kind);
    }

    private static void putV(float[] data, int off, float x, float y, float z,
                             float u, float v, float cx0, float cy0, float cx1, float cy1, float kind) {
        data[off]     = x;
        data[off + 1] = y;
        data[off + 2] = z;
        data[off + 3] = u;
        data[off + 4] = v;
        data[off + 5] = cx0;
        data[off + 6] = cy0;
        data[off + 7] = cx1;
        data[off + 8] = cy1;
        data[off + 9] = kind;
    }

    private void initBuffers() {
        if (vaoOpaque == 0) {
            vaoOpaque = GL30.glGenVertexArrays();
            vboOpaque = GL15.glGenBuffers();

            GL30.glBindVertexArray(vaoOpaque);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboOpaque);

            for (int i = 0; i < 3; i++) GL20.glEnableVertexAttribArray(i);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 9 * 4, 0L);
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 9 * 4, 12L);
            GL20.glVertexAttribPointer(2, 3, GL11.GL_FLOAT, false, 9 * 4, 24L);

            for (int i = 3; i < 8; i++) GL20.glDisableVertexAttribArray(i);
            GL20.glVertexAttrib4f(3, 0.0f, 0.0f, 0.0f, 0.0f);
            GL30.glBindVertexArray(0);
        }

        if (vaoTextured == 0) {
            vaoTextured = GL30.glGenVertexArrays();
            vboTextured = GL15.glGenBuffers();

            GL30.glBindVertexArray(vaoTextured);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboTextured);

            for (int i = 0; i < 4; i++) GL20.glEnableVertexAttribArray(i);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 10 * 4, 0L);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 10 * 4, 12L);
            GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, 10 * 4, 20L);
            GL20.glVertexAttribPointer(3, 1, GL11.GL_FLOAT, false, 10 * 4, 36L);

            for (int i = 4; i < 8; i++) GL20.glDisableVertexAttribArray(i);
            GL20.glVertexAttrib4f(4, 0.0f, 0.0f, 0.0f, 0.0f);
            GL30.glBindVertexArray(0);
        }
    }

    public void clear() {
        doors.clear();
        windows.clear();
        curtains.clear();

        if (mModelCacheRelease != null) {
            try {
                mModelCacheRelease.invoke(null, this);
            } catch (Throwable ignored) {}
        }

        for (int texId : modelGpuTextures.values()) {
            if (texId > 0) GL11.glDeleteTextures(texId);
        }
        modelGpuTextures.clear();
    }
}