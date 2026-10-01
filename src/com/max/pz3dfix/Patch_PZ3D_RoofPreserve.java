package com.max.pz3dfix;

import me.zed_0xff.zombie_buddy.Patch;
import org.joml.Matrix4f;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

public class Patch_PZ3D_RoofPreserve {

    public static Class<?> chunkClass;
    public static Constructor<?> chunkCtor;

    public static Method mChunkKey;
    public static Method mChunkSignature;
    public static Method mChunkWalls;
    public static Method mChunkFloors;
    public static Method mChunkAll;
    public static Method mChunkTiles;
    public static Method mChunkFaces;
    public static Method mChunkProps;
    public static Method mChunkMaterials;
    public static Method mChunkTargets;
    public static Method mChunkRoofs;
    public static Method mChunkCeilings;
    public static Method mChunkIndoor;
    public static Method mChunkSquares;
    public static Method mChunkTrunks;
    public static Method mChunkWires;
    public static Method mChunkAssets;
    public static Method mChunkFurniture;
    public static Method mChunkSources;

    public static Class<?> keyClass;
    public static Method mKeyX;
    public static Method mKeyY;

    public static boolean reflectionOk = false;

    public static class RoofEntry {
        public final Object key;
        public final List<?> roofs;
        public final List<?> ceilings;
        public final int hash;
        public final int keyX;
        public final int keyY;
        public long lastSeen;

        public RoofEntry(Object key, List<?> roofs, List<?> ceilings, int keyX, int keyY) {
            this.key = key;
            this.roofs = roofs;
            this.ceilings = ceilings;
            this.hash = Objects.hash(roofs, ceilings);
            this.keyX = keyX;
            this.keyY = keyY;
            this.lastSeen = System.currentTimeMillis();
        }
    }

    public static class MergedCacheEntry {
        public final long liveSig;
        public final int roofHash;
        public final Object mergedChunk;

        public MergedCacheEntry(long liveSig, int roofHash, Object mergedChunk) {
            this.liveSig = liveSig;
            this.roofHash = roofHash;
            this.mergedChunk = mergedChunk;
        }
    }

    public static final Map<Object, RoofEntry> persistentRoofs = new HashMap<>();
    public static final Map<Object, MergedCacheEntry> mergedCache = new HashMap<>();
    public static long lastPruneTime = 0;

    static {
        try {
            chunkClass = Class.forName("com.pavelvoronin.pz3d.WorldStream$Chunk");
            keyClass = Class.forName("com.pavelvoronin.pz3d.WorldStream$Key");

            chunkCtor = chunkClass.getDeclaredConstructor(
                    keyClass, long.class, List.class, List.class, List.class,
                    List.class, List.class, List.class, Map.class, List.class,
                    List.class, List.class, List.class, int.class, List.class,
                    List.class, List.class, List.class, List.class
            );
            chunkCtor.setAccessible(true);

            mChunkKey = chunkClass.getDeclaredMethod("key");
            mChunkKey.setAccessible(true);
            mChunkSignature = chunkClass.getDeclaredMethod("signature");
            mChunkSignature.setAccessible(true);
            mChunkWalls = chunkClass.getDeclaredMethod("walls");
            mChunkWalls.setAccessible(true);
            mChunkFloors = chunkClass.getDeclaredMethod("floors");
            mChunkFloors.setAccessible(true);
            mChunkAll = chunkClass.getDeclaredMethod("all");
            mChunkAll.setAccessible(true);
            mChunkTiles = chunkClass.getDeclaredMethod("tiles");
            mChunkTiles.setAccessible(true);
            mChunkFaces = chunkClass.getDeclaredMethod("faces");
            mChunkFaces.setAccessible(true);
            mChunkProps = chunkClass.getDeclaredMethod("props");
            mChunkProps.setAccessible(true);
            mChunkMaterials = chunkClass.getDeclaredMethod("materials");
            mChunkMaterials.setAccessible(true);
            mChunkTargets = chunkClass.getDeclaredMethod("targets");
            mChunkTargets.setAccessible(true);
            mChunkRoofs = chunkClass.getDeclaredMethod("roofs");
            mChunkRoofs.setAccessible(true);
            mChunkCeilings = chunkClass.getDeclaredMethod("ceilings");
            mChunkCeilings.setAccessible(true);
            mChunkIndoor = chunkClass.getDeclaredMethod("indoor");
            mChunkIndoor.setAccessible(true);
            mChunkSquares = chunkClass.getDeclaredMethod("squares");
            mChunkSquares.setAccessible(true);
            mChunkTrunks = chunkClass.getDeclaredMethod("trunks");
            mChunkTrunks.setAccessible(true);
            mChunkWires = chunkClass.getDeclaredMethod("wires");
            mChunkWires.setAccessible(true);
            mChunkAssets = chunkClass.getDeclaredMethod("assets");
            mChunkAssets.setAccessible(true);
            mChunkFurniture = chunkClass.getDeclaredMethod("furniture");
            mChunkFurniture.setAccessible(true);
            mChunkSources = chunkClass.getDeclaredMethod("sources");
            mChunkSources.setAccessible(true);

            mKeyX = keyClass.getDeclaredMethod("x");
            mKeyX.setAccessible(true);
            mKeyY = keyClass.getDeclaredMethod("y");
            mKeyY.setAccessible(true);

            reflectionOk = true;
            System.out.println("[PZ3DFix] RoofPreserve (Live RAM Mode) initialized successfully.");
        } catch (Throwable t) {
            System.err.println("[PZ3DFix] Failed to initialize reflection for RoofPreserve:");
            t.printStackTrace();
        }
    }

    // =========================================================================
    // 1. Hook WorldStream.submitExtensions: Preserve live roofs in the near bubble
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.WorldStream", methodName = "submitExtensions")
    public static class StreamExtensionsHook {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(value = 0, readOnly = false) List<Object> list,
                @Patch.Argument(1) float camX,
                @Patch.Argument(2) float camY,
                @Patch.Argument(3) float camZ,
                @Patch.Argument(4) Object[] objArr
        ) {
            if (!reflectionOk || list == null || list.isEmpty()) {
                return;
            }

            try {
                long now = System.currentTimeMillis();

                int camChunkX = Math.floorDiv((int) camX, 8);
                int camChunkY = Math.floorDiv((int) camY, 8);

                // Harvest live roofs directly from active RAM chunks
                for (Object chunk : list) {
                    List<?> cRoofs = (List<?>) mChunkRoofs.invoke(chunk);
                    List<?> cCeilings = (List<?>) mChunkCeilings.invoke(chunk);
                    boolean hasRoofs = (cRoofs != null && !cRoofs.isEmpty());
                    boolean hasCeilings = (cCeilings != null && !cCeilings.isEmpty());

                    if (hasRoofs || hasCeilings) {
                        Object key = mChunkKey.invoke(chunk);
                        int kX = (int) mKeyX.invoke(key);
                        int kY = (int) mKeyY.invoke(key);
                        persistentRoofs.put(key, new RoofEntry(key, cRoofs, cCeilings, kX, kY));
                    }
                }

                // Keep only roofs within the active 7-chunk live bubble (56m)
                if (now - lastPruneTime > 3000L && (camX != 0.0f || camY != 0.0f)) {
                    lastPruneTime = now;
                    Iterator<Map.Entry<Object, RoofEntry>> it = persistentRoofs.entrySet().iterator();
                    while (it.hasNext()) {
                        RoofEntry re = it.next().getValue();
                        if (Math.abs(re.keyX - camChunkX) > Patch_PZ3D_ToggleSpeedup.MAX_NEAR_CHUNK_RADIUS
                                || Math.abs(re.keyY - camChunkY) > Patch_PZ3D_ToggleSpeedup.MAX_NEAR_CHUNK_RADIUS) {
                            it.remove();
                        }
                    }
                    mergedCache.keySet().retainAll(persistentRoofs.keySet());
                }

                List<Object> workingList = list;
                boolean copied = false;

                for (int i = 0; i < workingList.size(); i++) {
                    Object chunk = workingList.get(i);
                    Object key = mChunkKey.invoke(chunk);

                    RoofEntry entry = persistentRoofs.get(key);
                    if (entry == null) continue;
                    entry.lastSeen = now;

                    List<?> cRoofs = (List<?>) mChunkRoofs.invoke(chunk);
                    List<?> cCeilings = (List<?>) mChunkCeilings.invoke(chunk);

                    boolean cHasRoofs = (cRoofs != null && !cRoofs.isEmpty());
                    boolean cHasCeilings = (cCeilings != null && !cCeilings.isEmpty());

                    if (cHasRoofs && cHasCeilings) continue;

                    long liveSig = (long) mChunkSignature.invoke(chunk);
                    MergedCacheEntry cachedMerged = mergedCache.get(key);

                    if (cachedMerged != null && cachedMerged.liveSig == liveSig && cachedMerged.roofHash == entry.hash) {
                        if (workingList.get(i) != cachedMerged.mergedChunk) {
                            if (!copied) {
                                try {
                                    workingList.set(i, cachedMerged.mergedChunk);
                                } catch (UnsupportedOperationException e) {
                                    workingList = new ArrayList<>(list);
                                    workingList.set(i, cachedMerged.mergedChunk);
                                    copied = true;
                                }
                            } else {
                                workingList.set(i, cachedMerged.mergedChunk);
                            }
                        }
                        continue;
                    }

                    List<?> finalRoofs = cHasRoofs ? cRoofs : entry.roofs;
                    List<?> finalCeilings = cHasCeilings ? cCeilings : entry.ceilings;
                    long finalSig = liveSig * 31L + entry.hash;

                    Object mergedChunk = chunkCtor.newInstance(
                            key,
                            finalSig,
                            mChunkWalls.invoke(chunk),
                            mChunkFloors.invoke(chunk),
                            mChunkAll.invoke(chunk),
                            mChunkTiles.invoke(chunk),
                            mChunkFaces.invoke(chunk),
                            mChunkProps.invoke(chunk),
                            mChunkMaterials.invoke(chunk),
                            mChunkTargets.invoke(chunk),
                            finalRoofs,
                            finalCeilings,
                            mChunkIndoor.invoke(chunk),
                            mChunkSquares.invoke(chunk),
                            mChunkTrunks.invoke(chunk),
                            mChunkWires.invoke(chunk),
                            mChunkAssets.invoke(chunk),
                            mChunkFurniture.invoke(chunk),
                            mChunkSources.invoke(chunk)
                    );

                    mergedCache.put(key, new MergedCacheEntry(liveSig, entry.hash, mergedChunk));

                    if (!copied) {
                        try {
                            workingList.set(i, mergedChunk);
                        } catch (UnsupportedOperationException e) {
                            workingList = new ArrayList<>(list);
                            workingList.set(i, mergedChunk);
                            copied = true;
                        }
                    } else {
                        workingList.set(i, mergedChunk);
                    }
                }

                if (copied) {
                    list = workingList;
                }

            } catch (Throwable t) {
                System.err.println("[PZ3DFix] Error in RoofPreserve live streaming: " + t.getMessage());
            }
        }
    }

    // =========================================================================
    // 2. Hook ViewMath.projection: Extend camera far plane from 160m to 2200m
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.ViewMath", methodName = "projection")
    public static class ProjectionFarPlaneHook {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) float aspect,
                @Patch.Argument(1) float x,
                @Patch.Argument(2) float y,
                @Patch.Argument(3) float z,
                @Patch.Argument(4) float dx,
                @Patch.Argument(5) float dy,
                @Patch.Argument(6) float dz,
                @Patch.Argument(7) float fov,
                @Patch.Return(readOnly = false) Matrix4f result
        ) {
            try {
                result = new Matrix4f()
                        .perspective(fov, aspect, 0.02f, 2200.0f)
                        .lookAt(x, -y, z, x + dx, (-y) - dy, z + dz, 0.0f, 0.0f, 1.0f)
                        .scale(1.0f, -1.0f, 1.0f);
            } catch (Throwable ignored) {
            }
        }
    }
}