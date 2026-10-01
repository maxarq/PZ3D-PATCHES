package com.max.pz3dfix;

import com.pavelvoronin.pz3d.WorldMirror;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;
import zombie.core.textures.Texture;
import zombie.iso.*;
import zombie.iso.areas.IsoRoom;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;

public class Patch_PZ3D_ToggleSpeedup {

    public static final int MAX_NEAR_CHUNK_RADIUS = 7;
    public static final float MAX_ENTRY_DISTANCE = 32.0f;

    public static volatile WorldMirror currentMirror = null;

    // WorldMirror reflection fields
    public static Field fBx;
    public static Field fBu;
    public static Field fVb;
    public static Field fZo;
    public static Field fBv;
    public static Field fBw;
    public static Field fBs;
    public static Field fBt;
    public static Field fDirty;
    public static Field fBy;
    public static Field fBr;
    public static Field fUj;
    public static Field fUk;
    public static Field fUl;
    public static Field fAC;
    public static Field fUploads;

    // Capture reflection fields
    public static Field fCaptureKey;
    public static Field fCaptureLo;
    public static Field fCaptureHi;
    public static Field fCaptureZ;
    public static Field fCaptureCx;
    public static Field fCaptureCy;
    public static Field fCaptureNativeChunk;
    public static Field fCaptureStates;
    public static Field fCaptureConditions;

    // Engine methods & constructors
    public static Method mMirrorUpdate;
    public static Method mCollisionReady;
    public static Method mNearReady;
    public static Method mBridgeLevel;
    public static Method mKeyX;
    public static Method mKeyY;
    public static Method mBtRemove;
    public static Method mSubmitExtensions;
    public static Method mSessionSnapshot;
    public static Method mStreamQueued;
    public static Method mStreamInvalidate;
    public static Method mUploadsInvalidate;

    // Main, BuildingSubsystem & SurfacePixels fields
    public static Field fMainLd;
    public static Field fSurfacePixelsDeferred;
    public static Field fBuildingBk;

    public static Constructor<?> ctorKey;
    public static Constructor<?> ctorWallKey;
    public static Constructor<?> ctorWallCached;
    public static Constructor<?> ctorWallSample;
    public static boolean reflectionOk = false;

    public static long lastEntryLogTime = 0L;
    public static long lastSubmitTime = 0L;

    static {
        try {
            Class<?> wmClass = Class.forName("com.pavelvoronin.pz3d.WorldMirror");
            fBx = wmClass.getDeclaredField("Bx");
            fBx.setAccessible(true);
            fBu = wmClass.getDeclaredField("Bu");
            fBu.setAccessible(true);
            fBv = wmClass.getDeclaredField("Bv");
            fBv.setAccessible(true);
            fBw = wmClass.getDeclaredField("Bw");
            fBw.setAccessible(true);
            fBs = wmClass.getDeclaredField("Bs");
            fBs.setAccessible(true);
            fBt = wmClass.getDeclaredField("Bt");
            fBt.setAccessible(true);
            fDirty = wmClass.getDeclaredField("dirty");
            fDirty.setAccessible(true);
            fBy = wmClass.getDeclaredField("By");
            fBy.setAccessible(true);
            fBr = wmClass.getDeclaredField("Br");
            fBr.setAccessible(true);
            fUj = wmClass.getDeclaredField("uj");
            fUj.setAccessible(true);
            fUk = wmClass.getDeclaredField("uk");
            fUk.setAccessible(true);
            fUl = wmClass.getDeclaredField("ul");
            fUl.setAccessible(true);
            fAC = wmClass.getDeclaredField("AC");
            fAC.setAccessible(true);
            fUploads = wmClass.getDeclaredField("uploads");
            fUploads.setAccessible(true);

            mMirrorUpdate = wmClass.getDeclaredMethod("update", float.class, float.class, float.class, boolean.class, long.class);
            mMirrorUpdate.setAccessible(true);

            Class<?> streamClass = Class.forName("com.pavelvoronin.pz3d.WorldStream");
            mSubmitExtensions = streamClass.getDeclaredMethod("submitExtensions", List.class, float.class, float.class, float.class, Object[].class);
            mSubmitExtensions.setAccessible(true);
            mStreamQueued = streamClass.getDeclaredMethod("queued");
            mStreamQueued.setAccessible(true);
            mStreamInvalidate = streamClass.getDeclaredMethod("invalidate");
            mStreamInvalidate.setAccessible(true);

            Class<?> uploadClass = Class.forName("com.pavelvoronin.pz3d.SceneUpload");
            mUploadsInvalidate = uploadClass.getDeclaredMethod("invalidate");
            mUploadsInvalidate.setAccessible(true);

            Class<?> sessionClass = fAC.getType();
            mSessionSnapshot = sessionClass.getDeclaredMethod("snapshot");
            mSessionSnapshot.setAccessible(true);

            Class<?> vcmClass = fBt.getType();
            Class<?> keyClass = Class.forName("com.pavelvoronin.pz3d.WorldStream$Key");
            mBtRemove = vcmClass.getDeclaredMethod("remove", keyClass);
            mBtRemove.setAccessible(true);

            mCollisionReady = wmClass.getDeclaredMethod("collisionReady", float.class, float.class);
            mCollisionReady.setAccessible(true);

            Class<?> sceneClass = Class.forName("com.pavelvoronin.pz3d.WorldMirror$Scene");
            Class<?> sfClass = Class.forName("com.pavelvoronin.pz3d.StreamFade");
            mNearReady = sfClass.getDeclaredMethod("nearReady", sceneClass, float.class, float.class);
            mNearReady.setAccessible(true);

            fVb = sfClass.getDeclaredField("vb");
            fVb.setAccessible(true);

            Class<?> pbClass = Class.forName("com.pavelvoronin.pz3d.PositionBridge");
            mBridgeLevel = pbClass.getDeclaredMethod("level", float.class);
            mBridgeLevel.setAccessible(true);

            mKeyX = keyClass.getDeclaredMethod("x");
            mKeyX.setAccessible(true);
            mKeyY = keyClass.getDeclaredMethod("y");
            mKeyY.setAccessible(true);
            ctorKey = keyClass.getDeclaredConstructor(int.class, int.class);
            ctorKey.setAccessible(true);

            Class<?> wmMatClass = Class.forName("com.pavelvoronin.pz3d.WallMaterials");
            fZo = wmMatClass.getDeclaredField("zO");
            fZo.setAccessible(true);

            Class<?> wallKeyClass = Class.forName("com.pavelvoronin.pz3d.WallMaterials$Key");
            ctorWallKey = wallKeyClass.getDeclaredConstructor(Object.class, int.class, boolean.class, boolean.class);
            ctorWallKey.setAccessible(true);

            Class<?> wallSampleClass = Class.forName("com.pavelvoronin.pz3d.WallMaterials$Sample");
            ctorWallSample = wallSampleClass.getDeclaredConstructor(Texture.class, boolean.class);
            ctorWallSample.setAccessible(true);

            Class<?> wallCachedClass = Class.forName("com.pavelvoronin.pz3d.WallMaterials$Cached");
            ctorWallCached = wallCachedClass.getDeclaredConstructor(wallSampleClass, long.class);
            ctorWallCached.setAccessible(true);

            Class<?> captureClass = Class.forName("com.pavelvoronin.pz3d.WorldMirror$Capture");
            fCaptureKey = captureClass.getDeclaredField("key");
            fCaptureKey.setAccessible(true);
            fCaptureLo = captureClass.getDeclaredField("lo");
            fCaptureLo.setAccessible(true);
            fCaptureHi = captureClass.getDeclaredField("hi");
            fCaptureHi.setAccessible(true);
            fCaptureZ = captureClass.getDeclaredField("z");
            fCaptureZ.setAccessible(true);
            fCaptureCx = captureClass.getDeclaredField("cx");
            fCaptureCx.setAccessible(true);
            fCaptureCy = captureClass.getDeclaredField("cy");
            fCaptureCy.setAccessible(true);
            fCaptureNativeChunk = captureClass.getDeclaredField("nativeChunk");
            fCaptureNativeChunk.setAccessible(true);
            fCaptureStates = captureClass.getDeclaredField("states");
            fCaptureStates.setAccessible(true);
            fCaptureConditions = captureClass.getDeclaredField("conditions");
            fCaptureConditions.setAccessible(true);

            try {
                Class<?> mainClass = Class.forName("com.pavelvoronin.pz3d.Main");
                fMainLd = mainClass.getDeclaredField("lD");
                fMainLd.setAccessible(true);
            } catch (Throwable ignored) {
            }

            try {
                Class<?> spClass = Class.forName("com.pavelvoronin.pz3d.SurfacePixels");
                fSurfacePixelsDeferred = spClass.getDeclaredField("deferred");
                fSurfacePixelsDeferred.setAccessible(true);
            } catch (Throwable ignored) {
            }

            reflectionOk = true;
            System.out.println("[PZ3DFix] Anti-Deadlock, Fast-Cache & Driving Pipeline initialized.");
        } catch (Throwable t) {
            System.err.println("[PZ3DFix] Failed to initialize reflection for ToggleSpeedup:");
            t.printStackTrace();
        }
    }

    public static void disarmBuildingSubsystem(Object subsystem) {
        if (subsystem == null) return;
        try {
            if (fBuildingBk == null) {
                fBuildingBk = subsystem.getClass().getDeclaredField("bk");
                fBuildingBk.setAccessible(true);
            }
            Object bk = fBuildingBk.get(subsystem);
            if (bk != null) {
                ((ExecutorService) bk).shutdownNow();
                fBuildingBk.set(subsystem, null);
                System.out.println("[PZ3DFix] Disarmed PZ3D disk building loader (bk=null). Live RAM bubble active.");
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Re-scans an 8x8 chunk on true structural changes without deleting it from Bs,
     * so collisionReady never returns false and movement never stalls.
     */
    public static void triggerImmediateChunkReload(int tileX, int tileY) {
        WorldMirror mirror = currentMirror;
        if (!reflectionOk || mirror == null) return;

        try {
            int cx = Math.floorDiv(tileX, 8);
            int cy = Math.floorDiv(tileY, 8);
            Object key = ctorKey.newInstance(cx, cy);

            @SuppressWarnings("unchecked")
            Set<Object> bx = (Set<Object>) fBx.get(mirror);
            if (bx != null) {
                bx.add(key);
            }

            Object bv = fBv.get(mirror);
            if (bv != null) {
                Object bvKey = fCaptureKey.get(bv);
                if (bvKey != null && !bvKey.equals(key)) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> bw = (Map<Object, Object>) fBw.get(mirror);
                    if (bw != null && bw.size() < 4) {
                        bw.put(bvKey, bv);
                    }
                    fBv.set(mirror, null);
                }
            }

            fBy.setLong(mirror, 0L);
            fDirty.setBoolean(mirror, true);
        } catch (Throwable ignored) {
        }
    }

    public static void pruneDistant(WorldMirror mirror, int pcx, int pcy) {
        if (mirror == null) return;
        try {
            // Prune InteractiveLayer in exact lockstep with chunk pruning
            InteractiveLayer.getInstance().pruneDistant(pcx, pcy, MAX_NEAR_CHUNK_RADIUS);

            @SuppressWarnings("unchecked")
            Map<Object, Object> bs = (Map<Object, Object>) fBs.get(mirror);
            @SuppressWarnings("unchecked")
            Set<Object> bu = (Set<Object>) fBu.get(mirror);
            @SuppressWarnings("unchecked")
            Map<Object, Object> bw = (Map<Object, Object>) fBw.get(mirror);
            Object bt = fBt.get(mirror);

            if (bs != null && !bs.isEmpty()) {
                Iterator<Map.Entry<Object, Object>> it = bs.entrySet().iterator();
                boolean anyRemoved = false;
                while (it.hasNext()) {
                    Object key = it.next().getKey();
                    int kx = (int) mKeyX.invoke(key);
                    int ky = (int) mKeyY.invoke(key);
                    if (Math.abs(kx - pcx) > MAX_NEAR_CHUNK_RADIUS || Math.abs(ky - pcy) > MAX_NEAR_CHUNK_RADIUS) {
                        it.remove();
                        if (mBtRemove != null && bt != null) {
                            mBtRemove.invoke(bt, key);
                        }
                        anyRemoved = true;
                    }
                }
                if (anyRemoved) {
                    fDirty.setBoolean(mirror, true);
                }
            }

            if (bu != null && !bu.isEmpty()) {
                bu.removeIf(key -> {
                    try {
                        int kx = (int) mKeyX.invoke(key);
                        int ky = (int) mKeyY.invoke(key);
                        return Math.abs(kx - pcx) > MAX_NEAR_CHUNK_RADIUS || Math.abs(ky - pcy) > MAX_NEAR_CHUNK_RADIUS;
                    } catch (Throwable t) {
                        return false;
                    }
                });
            }

            if (bw != null && !bw.isEmpty()) {
                bw.keySet().removeIf(key -> {
                    try {
                        int kx = (int) mKeyX.invoke(key);
                        int ky = (int) mKeyY.invoke(key);
                        return Math.abs(kx - pcx) > 2 || Math.abs(ky - pcy) > 2;
                    } catch (Throwable t) {
                        return false;
                    }
                });
                while (bw.size() >= 4) {
                    Iterator<?> bIter = bw.keySet().iterator();
                    if (bIter.hasNext()) {
                        bIter.next();
                        bIter.remove();
                    } else {
                        break;
                    }
                }
            }

            Object bv = fBv.get(mirror);
            if (bv != null) {
                Object bvKey = fCaptureKey.get(bv);
                if (bvKey != null) {
                    int bkx = (int) mKeyX.invoke(bvKey);
                    int bky = (int) mKeyY.invoke(bvKey);
                    if (Math.abs(bkx - pcx) > MAX_NEAR_CHUNK_RADIUS || Math.abs(bky - pcy) > MAX_NEAR_CHUNK_RADIUS) {
                        fBv.set(mirror, null);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // =========================================================================
    // 0. Hook WorldMirror.close: Clean up InteractiveLayer completely on leave/reload
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.WorldMirror", methodName = "close")
    public static class WorldMirrorCloseHook {
        @Patch.OnExit
        public static void exit() {
            InteractiveLayer.getInstance().clear();
        }
    }

    // =========================================================================
    // 1. Hook BuildingSubsystem: Disarm disk whole-building loader
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.BuildingSubsystem", methodName = "start")
    public static class BuildingSubsystemStartHook {
        @Patch.OnExit
        public static void exit(@Patch.This Object subsystem) {
            disarmBuildingSubsystem(subsystem);
        }
    }

    @Patch(className = "com.pavelvoronin.pz3d.BuildingSubsystem", methodName = "update")
    public static class BuildingSubsystemUpdateHook {
        @Patch.OnEnter
        public static void enter(@Patch.This Object subsystem) {
            disarmBuildingSubsystem(subsystem);
        }
    }

    // =========================================================================
    // 2. Hook WorldMirror.update: Budget Throttling & Live Origin Propagation
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.WorldMirror", methodName = "update")
    public static class WorldMirrorBudgetHook {
        @Patch.OnEnter
        public static void enter(
                @Patch.This WorldMirror mirror,
                @Patch.Argument(0) float f,
                @Patch.Argument(1) float f2,
                @Patch.Argument(2) float f3,
                @Patch.Argument(3) boolean prewarm,
                @Patch.Argument(value = 4, readOnly = false) long budgetNanos
        ) {
            currentMirror = mirror;

            if (prewarm) {
                if (budgetNanos > 2500000L) budgetNanos = 2500000L;
            } else {
                if (budgetNanos > 3000000L) budgetNanos = 3000000L;
            }

            if (!reflectionOk || mirror == null) return;

            try {
                int pcx = Math.floorDiv((int) Math.floor(f), 8);
                int pcy = Math.floorDiv((int) Math.floor(f2), 8);
                pruneDistant(mirror, pcx, pcy);
            } catch (Throwable ignored) {
            }
        }

        @Patch.OnExit
        public static void exit(
                @Patch.This WorldMirror mirror,
                @Patch.Argument(0) float f,
                @Patch.Argument(1) float f2,
                @Patch.Argument(2) float f3
        ) {
            if (!reflectionOk || mirror == null) return;

            try {
                WorldMirror.Scene sc = mirror.scene();
                if (sc == null || sc.squares() <= 0) return;

                double distFromScene = Math.hypot(sc.ox() - f, sc.oy() - f2);
                long nanoNow = System.nanoTime();

                if (distFromScene > 16.0) {
                    if (nanoNow - lastSubmitTime >= 250_000_000L) {
                        Object stream = fBr.get(mirror);
                        if (stream != null && mSubmitExtensions != null) {
                            @SuppressWarnings("unchecked")
                            Map<Object, Object> bs = (Map<Object, Object>) fBs.get(mirror);
                            if (bs != null && !bs.isEmpty()) {
                                List<Object> meshList = new ArrayList<>(bs.size());
                                Method mMesh = null;
                                for (Object cached : bs.values()) {
                                    if (mMesh == null) {
                                        mMesh = cached.getClass().getDeclaredMethod("mesh");
                                        mMesh.setAccessible(true);
                                    }
                                    meshList.add(mMesh.invoke(cached));
                                }

                                float uj = fUj.getFloat(mirror);
                                float uk = fUk.getFloat(mirror);
                                float ul = fUl.getFloat(mirror);
                                Object ac = fAC.get(mirror);
                                Object[] snapshot = (Object[]) mSessionSnapshot.invoke(ac);

                                mSubmitExtensions.invoke(stream, meshList, uj, uk, ul, snapshot);

                                fDirty.setBoolean(mirror, false);
                                fBy.setLong(mirror, nanoNow);
                                lastSubmitTime = nanoNow;
                            }
                        }
                    } else {
                        fDirty.setBoolean(mirror, true);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // =========================================================================
    // 3. Prevent SurfacePixels from throwing Pending
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.SurfacePixels", methodName = "opaque")
    public static class SurfacePixelsOpaqueHook {
        @Patch.OnEnter
        public static void enter() {
            if (fSurfacePixelsDeferred != null) {
                try {
                    fSurfacePixelsDeferred.setBoolean(null, false);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // =========================================================================
    // 4. Hook RegionWork.select: Wipe age inflation for chunks outside the player area
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.RegionWork", methodName = "select")
    public static class RegionWorkSelectHook {
        public static Field fRegionWorkQb = null;
        public static boolean qbInit = false;

        @Patch.OnEnter
        public static void enter(
                @Patch.This Object regionWork,
                @Patch.Argument(0) List<?> list,
                @Patch.Argument(1) List<?> list2,
                @Patch.Argument(2) Object[] objArr,
                @Patch.Argument(3) Object[] objArr2,
                @Patch.Argument(4) float f,
                @Patch.Argument(5) float f2,
                @Patch.Argument(6) int allowance
        ) {
            if (regionWork == null) return;
            try {
                if (!qbInit) {
                    fRegionWorkQb = regionWork.getClass().getDeclaredField("qb");
                    fRegionWorkQb.setAccessible(true);
                    qbInit = true;
                }
                if (fRegionWorkQb != null) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Long> qb = (Map<Object, Long>) fRegionWorkQb.get(regionWork);
                    if (qb != null && !qb.isEmpty()) {
                        int pcx = Math.floorDiv((int) Math.floor(f), 8);
                        int pcy = Math.floorDiv((int) Math.floor(f2), 8);
                        qb.keySet().removeIf(key -> {
                            try {
                                int kx = (int) mKeyX.invoke(key);
                                int ky = (int) mKeyY.invoke(key);
                                return Math.abs(kx - pcx) > MAX_NEAR_CHUNK_RADIUS || Math.abs(ky - pcy) > MAX_NEAR_CHUNK_RADIUS;
                            } catch (Throwable t) {
                                return false;
                            }
                        });
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // =========================================================================
    // 5. Hook Main.tick(): Prevent lD from trapping the tick loop
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.Main", methodName = "tick")
    public static class MainTickHook {
        public static long ldStuckTime = 0L;

        @Patch.OnEnter
        public static void enter() {
            if (!reflectionOk || fMainLd == null) return;
            try {
                boolean ld = fMainLd.getBoolean(null);
                if (ld) {
                    IsoPlayer player = IsoPlayer.getInstance();
                    long now = System.currentTimeMillis();
                    if ((player != null && player.getVehicle() != null) || (ldStuckTime != 0L && now - ldStuckTime > 300L)) {
                        fMainLd.setBoolean(null, false);
                        ldStuckTime = 0L;
                    } else if (ldStuckTime == 0L) {
                        ldStuckTime = now;
                    }
                } else {
                    ldStuckTime = 0L;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // =========================================================================
    // 6. Silence Polling-Based Invalidation (Replaced by InteractiveLayer)
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.WorldMirror$VisualState", methodName = "changed")
    public static class VisualStateChangedHook {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean changed) {
            changed = false;
        }
    }

    // =========================================================================
    // 7. Silence VisualConditionWatch Polling
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.VisualConditionWatch", methodName = "changed")
    public static class VisualConditionWatchChangedHook {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean changed) {
            changed = false;
        }
    }

    // =========================================================================
    // 8. Hook Capture.step: Vertical Window Clamping [Z-4, Z+5] + No Abort
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.WorldMirror$Capture", methodName = "step")
    public static class CaptureStepHook {
        @Patch.OnEnter
        public static void enter(@Patch.This Object capture) {
            if (!reflectionOk || capture == null) return;

            try {
                IsoPlayer player = IsoPlayer.getInstance();
                int pz = (player != null) ? Math.round(player.getZ()) : 0;

                int nativeMin = fCaptureLo.getInt(capture);
                int nativeMax = fCaptureHi.getInt(capture);

                int clampedLo = Math.max(nativeMin, pz - 4);
                int clampedHi = Math.min(nativeMax, pz + 5);

                fCaptureLo.setInt(capture, clampedLo);
                fCaptureHi.setInt(capture, clampedHi);

                int currentZ = fCaptureZ.getInt(capture);
                if (currentZ < clampedLo) {
                    fCaptureZ.setInt(capture, clampedLo);
                }

                if (currentZ > clampedHi) {
                    @SuppressWarnings("unchecked")
                    List<Object> states = (List<Object>) fCaptureStates.get(capture);
                    if (states != null) states.clear();

                    @SuppressWarnings("unchecked")
                    List<Object> conditions = (List<Object>) fCaptureConditions.get(capture);
                    if (conditions != null) conditions.clear();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // =========================================================================
    // 9. Hook Capture.current: Prevent Aborts on Dynamic Height / Chunk Shifts
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.WorldMirror$Capture", methodName = "current")
    public static class CaptureCurrentHook {
        @Patch.OnExit
        public static void exit(
                @Patch.This Object capture,
                @Patch.Return(readOnly = false) boolean isCurrent
        ) {
            if (!isCurrent && reflectionOk && capture != null) {
                try {
                    IsoChunk nativeChunk = (IsoChunk) fCaptureNativeChunk.get(capture);
                    int cx = fCaptureCx.getInt(capture);
                    int cy = fCaptureCy.getInt(capture);
                    IsoCell cell = (IsoWorld.instance != null) ? IsoWorld.instance.currentCell : null;
                    IsoChunk currentChunk = (cell != null) ? cell.getChunkForGridSquare(cx, cy, 0) : null;
                    if (nativeChunk == currentChunk && nativeChunk != null) {
                        isCurrent = true;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // =========================================================================
    // 10. Hook SceneUpload.canPrepare: Prevent WorldStream Admission Deadlock
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.SceneUpload", methodName = "canPrepare")
    public static class SceneUploadCanPrepareHook {
        @Patch.OnExit
        public static void exit(
                @Patch.This Object upload,
                @Patch.Return(readOnly = false) boolean canPrepare
        ) {
            canPrepare = true;
        }
    }

    // =========================================================================
    // 11. Balanced SceneUpload Budget (4 MB / 3 ms per frame)
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.SceneUpload", methodName = "step")
    public static class SceneUploadBudgetHook {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) WorldMirror.Scene scene,
                @Patch.Argument(value = 1, readOnly = false) long timeBudgetNanos,
                @Patch.Argument(value = 2, readOnly = false) int byteBudget
        ) {
            if (timeBudgetNanos > 3000000L) timeBudgetNanos = 3000000L;
            if (byteBudget > 4194304) byteBudget = 4194304;
        }
    }

    // =========================================================================
    // 12. Expand RegionWork chunk allowance to 512
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.WorkPool", methodName = "regionAllowance")
    public static class RegionAllowanceHook {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) int allowance) {
            allowance = 512;
        }
    }

    // =========================================================================
    // 13. Hook WallMaterials.reverse: Pre-seed cache
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.WallMaterials", methodName = "reverse")
    public static class WallMaterialsReverseHook {
        @Patch.OnEnter
        public static void enter(
                @Patch.This Object wallMaterials,
                @Patch.Argument(0) IsoGridSquare sq,
                @Patch.Argument(1) boolean north,
                @Patch.Argument(2) IsoObject wallObj
        ) {
            if (!reflectionOk || wallMaterials == null || sq == null || wallObj == null) return;

            try {
                if (IsoWorld.instance == null || IsoWorld.instance.currentCell == null) return;

                int adjX = sq.x - (north ? 0 : 1);
                int adjY = sq.y - (north ? 1 : 0);
                IsoGridSquare adjSq = IsoWorld.instance.currentCell.getGridSquare(adjX, adjY, sq.z);

                IsoRoom room1 = sq.getRoom();
                IsoRoom room2 = (adjSq != null) ? adjSq.getRoom() : null;
                if (room1 == room2) return;

                boolean z2 = (room2 == null);
                Object building = z2 ? (room1 == null ? null : room1.getBuilding()) : room2;
                if (building == null) return;

                @SuppressWarnings("unchecked")
                Map<Object, Object> zO = (Map<Object, Object>) fZo.get(wallMaterials);
                if (zO == null) return;

                Object wallKey = ctorWallKey.newInstance(building, sq.z, north, z2);
                if (!zO.containsKey(wallKey)) {
                    Texture tex = (wallObj.getSprite() != null)
                            ? wallObj.getSprite().getTextureForCurrentFrame(IsoDirections.N, wallObj)
                            : null;

                    if (tex != null && tex.isReady()) {
                        Object sample = ctorWallSample.newInstance(tex, north);
                        Object cached = ctorWallCached.newInstance(sample, System.nanoTime() + 3000000000000L);
                        zO.put(wallKey, cached);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // =========================================================================
    // 14. Hook StreamFade.nearReady: Smart Gating
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.StreamFade", methodName = "nearReady")
    public static class StreamFadeNearReadyHook {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) WorldMirror.Scene scene,
                @Patch.Argument(1) float f,
                @Patch.Argument(2) float f2,
                @Patch.Return(readOnly = false) boolean ready
        ) {
            if (ready || !reflectionOk || scene == null) return;

            try {
                @SuppressWarnings("unchecked")
                Map<float[], Set<Object>> vbMap = (Map<float[], Set<Object>>) fVb.get(null);
                if (vbMap == null) return;

                Set<Object> set = vbMap.get(scene.vertices());
                if (set == null || set.isEmpty()) return;

                int pcx = Math.floorDiv((int) Math.floor(f), 8);
                int pcy = Math.floorDiv((int) Math.floor(f2), 8);

                Object playerKey = ctorKey.newInstance(pcx, pcy);
                if (!set.contains(playerKey)) return;

                int colMinX = Math.floorDiv((int) Math.floor(f - 1.5f), 8);
                int colMaxX = Math.floorDiv((int) Math.floor(f + 1.5f), 8);
                int colMinY = Math.floorDiv((int) Math.floor(f2 - 1.5f), 8);
                int colMaxY = Math.floorDiv((int) Math.floor(f2 + 1.5f), 8);

                for (int cy = colMinY; cy <= colMaxY; cy++) {
                    for (int cx = colMinX; cx <= colMaxX; cx++) {
                        Object colKey = ctorKey.newInstance(cx, cy);
                        if (!set.contains(colKey)) return;
                    }
                }

                int presentCount = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        Object key = ctorKey.newInstance(pcx + dy, pcy + dy);
                        if (set.contains(key)) presentCount++;
                    }
                }

                if (presentCount >= 6) {
                    ready = true;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // =========================================================================
    // 15. Hook WorldMirror.entryReady: Safe Tolerance
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.WorldMirror", methodName = "entryReady")
    public static class EntryReadyHook {
        @Patch.OnExit
        public static void exit(
                @Patch.This WorldMirror mirror,
                @Patch.Argument(0) float f,
                @Patch.Argument(1) float f2,
                @Patch.Argument(2) float f3,
                @Patch.Return(readOnly = false) boolean ready
        ) {
            if (ready) return;

            WorldMirror.Scene scene = (mirror != null) ? mirror.scene() : null;
            if (reflectionOk && mirror != null && scene != null && scene.squares() > 0) {
                try {
                    int level = (int) mBridgeLevel.invoke(null, f3);
                    float expectedOz = ((float) level) * 2.44949f;
                    boolean colReady = (boolean) mCollisionReady.invoke(mirror, f, f2);
                    boolean nearReady = (boolean) mNearReady.invoke(null, scene, f, f2);

                    float dist = (float) Math.hypot(scene.ox() - f, scene.oy() - f2);

                    if (dist <= MAX_ENTRY_DISTANCE && scene.oz() == expectedOz && colReady && nearReady) {
                        ready = true;
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }

            long now = System.currentTimeMillis();
            if (now - lastEntryLogTime > 1500L) {
                lastEntryLogTime = now;
                try {
                    System.out.println("[PZ3DFix-Diag] === 3D ENTRY BLOCKED === (dist=" +
                            (scene != null ? String.format(Locale.ROOT, "%.1fm", Math.hypot(scene.ox() - f, scene.oy() - f2)) : "N/A") + ")");
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    // =========================================================================
    // 16. Hook StreamFade$Ages.update: Pre-date birth time so actors are ready immediately
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.StreamFade$Ages", methodName = "update")
    public static class StreamFadeAgesUpdateHook {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) Set<?> set,
                @Patch.Argument(value = 1, readOnly = false) float f
        ) {
            f = f - 10.0f;
        }
    }

    // =========================================================================
    // 17. Hook StreamFade.upload: Turn off the streamFadeEnabled uniform
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.StreamFade", methodName = "upload")
    public static class StreamFadeUploadHook {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) int program,
                @Patch.Argument(1) WorldMirror.Scene scene,
                @Patch.Argument(value = 2, readOnly = false) boolean enabled
        ) {
            enabled = false;
        }
    }
}