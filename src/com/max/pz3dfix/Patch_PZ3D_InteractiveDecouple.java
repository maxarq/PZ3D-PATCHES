package com.max.pz3dfix;

import com.pavelvoronin.pz3d.Geometry;
import com.pavelvoronin.pz3d.WorldMirror;
import me.zed_0xff.zombie_buddy.Patch;
import org.joml.Matrix4f;
import zombie.core.textures.Texture;
import zombie.iso.IsoDirections;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.GridSquareEdgeFacingDirection;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
import zombie.iso.sprite.IsoSprite;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Patch_PZ3D_InteractiveDecouple {

    public static Field fWcgFaces;
    public static Field fWcgAssets;
    public static Field fWcgAB;
    public static Field fWcgAC;
    public static Method mAcVisual;
    public static Method mNeighborDoor;
    public static Method mStructural;
    public static Method mWideDoor;
    public static boolean wcgReflectionInit = false;

    public static void ensureWcgReflection(Object wcg) {
        if (!wcgReflectionInit && wcg != null) {
            try {
                Class<?> cls = wcg.getClass();
                fWcgFaces = cls.getDeclaredField("faces");
                fWcgFaces.setAccessible(true);

                fWcgAssets = cls.getDeclaredField("assets");
                fWcgAssets.setAccessible(true);

                fWcgAB = cls.getDeclaredField("AB");
                fWcgAB.setAccessible(true);

                fWcgAC = cls.getDeclaredField("AC");
                fWcgAC.setAccessible(true);

                Class<?> sessionCls = fWcgAC.getType();
                for (Method m : sessionCls.getDeclaredMethods()) {
                    if (m.getName().equals("visual") && m.getParameterCount() == 8) {
                        mAcVisual = m;
                        mAcVisual.setAccessible(true);
                        InteractiveLayer.mAcVisual = m;
                        break;
                    }
                }

                mNeighborDoor = WorldMirror.class.getDeclaredMethod("neighborDoor", int.class, int.class, int.class, boolean.class, int.class);
                mNeighborDoor.setAccessible(true);

                Class<?> wmMatClass = Class.forName("com.pavelvoronin.pz3d.WallMaterials");
                mStructural = wmMatClass.getDeclaredMethod("structural", IsoGridSquare.class, boolean.class);
                mStructural.setAccessible(true);

                mWideDoor = WorldMirror.class.getDeclaredMethod("wideDoor", IsoObject.class);
                mWideDoor.setAccessible(true);

            } catch (Throwable t) {
                System.err.println("[PZ3DFix] Error initializing WCG reflection: " + t.getMessage());
            }
            wcgReflectionInit = true;
        }
    }

    public static final class BoundarySnapshot {
        public int boxesStart;
        public int facesStart;
        public int assetsStart;
        public int abStart;
    }

    public static final ThreadLocal<BoundarySnapshot> currentBoundary = ThreadLocal.withInitial(BoundarySnapshot::new);

    @Patch(className = "com.pavelvoronin.pz3d.WorldCaptureGeometry", methodName = "addBoundary")
    public static class AddBoundaryHook {

        @Patch.OnEnter
        public static void enter(
                @Patch.This Object wcg,
                @Patch.Argument(0) List<Geometry.Box> list,
                @Patch.Argument(1) List<?> wires,
                @Patch.Argument(2) IsoGridSquare sq,
                @Patch.Argument(3) int tileX,
                @Patch.Argument(4) int tileY,
                @Patch.Argument(5) float floorZ,
                @Patch.Argument(6) boolean north
        ) {
            ensureWcgReflection(wcg);
            BoundarySnapshot snap = currentBoundary.get();
            snap.boxesStart = (list != null) ? list.size() : 0;
            try {
                if (fWcgFaces != null) {
                    List<?> faces = (List<?>) fWcgFaces.get(wcg);
                    snap.facesStart = (faces != null) ? faces.size() : 0;
                }
                if (fWcgAssets != null) {
                    List<?> assets = (List<?>) fWcgAssets.get(wcg);
                    snap.assetsStart = (assets != null) ? assets.size() : 0;
                }
                if (fWcgAB != null) {
                    List<?> ab = (List<?>) fWcgAB.get(wcg);
                    snap.abStart = (ab != null) ? ab.size() : 0;
                }
            } catch (Throwable ignored) {}
        }

        @Patch.OnExit
        public static void exit(
                @Patch.This Object wcg,
                @Patch.Argument(0) List<Geometry.Box> list,
                @Patch.Argument(1) List<?> wires,
                @Patch.Argument(2) IsoGridSquare sq,
                @Patch.Argument(3) int tileX,
                @Patch.Argument(4) int tileY,
                @Patch.Argument(5) float floorZ,
                @Patch.Argument(6) boolean north
        ) {
            if (sq == null) return;
            ensureWcgReflection(wcg);

            BoundarySnapshot snap = currentBoundary.get();

            GridSquareEdgeFacingDirection dir = north
                    ? GridSquareEdgeFacingDirection.NORTH_SOUTH
                    : GridSquareEdgeFacingDirection.EAST_WEST;

            IsoObject door = sq.getDoor(dir);
            IsoWindow window = sq.getWindow(dir);

            try {
                List<?> faces = (fWcgFaces != null) ? (List<?>) fWcgFaces.get(wcg) : null;
                List<?> assets = (fWcgAssets != null) ? (List<?>) fWcgAssets.get(wcg) : null;
                List<?> ab = (fWcgAB != null) ? (List<?>) fWcgAB.get(wcg) : null;
                Object ac = (fWcgAC != null) ? fWcgAC.get(wcg) : null;
                InteractiveLayer.getInstance().activeAc = ac;

                // =============================================================
                // 1. DOORS: Steal Pre-Resolved Model, Query Base Sprite For States
                // =============================================================
                if ((door instanceof IsoDoor) || (door instanceof IsoThumpable && ((IsoThumpable) door).isDoor())) {
                    boolean wide = false;
                    if (mWideDoor != null) {
                        wide = (boolean) mWideDoor.invoke(null, door);
                    } else if (door.getProperties() != null) {
                        wide = door.getProperties().has("DoubleDoor") || door.getProperties().has("GarageDoor");
                    }
                    boolean garage = door.getProperties() != null && door.getProperties().has("GarageDoor");

                    // Check neighbors in -1 (West/North) and +1 (East/South) directions
                    boolean hasNeighborNeg = false;
                    boolean hasNeighborPos = false;
                    if (mNeighborDoor != null) {
                        hasNeighborNeg = (boolean) mNeighborDoor.invoke(null, tileX, tileY, sq.getZ(), north, -1);
                        hasNeighborPos = (boolean) mNeighborDoor.invoke(null, tileX, tileY, sq.getZ(), north, 1);
                    } else if (sq.getCell() != null) {
                        int nx1 = tileX + (north ? -1 : 0);
                        int ny1 = tileY + (north ? 0 : -1);
                        IsoGridSquare sqNeg = sq.getCell().getGridSquare(nx1, ny1, sq.getZ());
                        if (sqNeg != null && sqNeg.getDoor(dir) != null) hasNeighborNeg = true;

                        int nx2 = tileX + (north ? 1 : 0);
                        int ny2 = tileY + (north ? 0 : 1);
                        IsoGridSquare sqPos = sq.getCell().getGridSquare(nx2, ny2, sq.getZ());
                        if (sqPos != null && sqPos.getDoor(dir) != null) hasNeighborPos = true;
                    }

                    boolean isDouble = wide || (door.getProperties() != null && door.getProperties().has("DoubleDoor")) || hasNeighborNeg || hasNeighborPos;

                    boolean isSecondLeaf = false;
                    if (isDouble) {
                        if (hasNeighborNeg && hasNeighborPos && mNeighborDoor != null) {
                            boolean hasFarPos = (boolean) mNeighborDoor.invoke(null, tileX, tileY, sq.getZ(), north, 2);
                            isSecondLeaf = !hasFarPos;
                        } else {
                            isSecondLeaf = hasNeighborNeg;
                        }
                    }

                    IsoObject structural = (mStructural != null) ? (IsoObject) mStructural.invoke(null, sq, north) : null;
                    boolean hasFrame = (structural != null && !wide);

                    int frameBoxes = 0;
                    int frameFaces = 0;

                    if (hasFrame) {
                        frameBoxes = (hasNeighborNeg ? 0 : 1) + (hasNeighborPos ? 0 : 1) + 1;
                        frameFaces = (structural != null && InteractiveLayer.getTexture(structural) != null) ? (2 + (hasNeighborNeg ? 0 : 1) + (hasNeighborPos ? 0 : 1)) : 0;
                    }

                    int keepBoxes = snap.boxesStart + frameBoxes;
                    int keepFaces = snap.facesStart + frameFaces;

                    Object placementClosed = null;
                    if (assets != null && assets.size() > snap.assetsStart) {
                        for (int idx = snap.assetsStart; idx < assets.size(); idx++) {
                            Object p = assets.get(idx);
                            if (p != null) {
                                placementClosed = p;
                                break;
                            }
                        }
                    }

                    IsoSprite spr = door.getSprite();
                    String baseSprName = (spr != null) ? spr.getName() : null;
                    float rot = north ? 0.0f : 90.0f;

                    if (placementClosed == null && baseSprName != null && ac != null && mAcVisual != null) {
                        placementClosed = InteractiveLayer.resolveVisualPlacement(ac, mAcVisual, baseSprName, door,
                                (float) tileX, (float) tileY, floorZ, rot, "closed", "default");
                    }

                    Object placementOpen = null;
                    if (baseSprName != null && ac != null && mAcVisual != null) {
                        placementOpen = InteractiveLayer.resolveVisualPlacement(ac, mAcVisual, baseSprName, door,
                                (float) tileX, (float) tileY, floorZ, rot, "open");
                    }

                    IsoDirections edge = (door instanceof IsoDoor)
                            ? ((IsoDoor) door).getSpriteEdge(false)
                            : ((IsoThumpable) door).getSpriteEdge(false);

                    boolean hingeEast = (edge == IsoDirections.E);
                    boolean hingeSouth = (edge == IsoDirections.S);

                    if (placementOpen == null && placementClosed != null) {
                        float hingeX = isDouble ? (north ? (isSecondLeaf ? tileX + 1.0f : tileX) : tileX) : (tileX + (hingeEast ? 1.0f : 0.0f));
                        float hingeY = isDouble ? (north ? tileY : (isSecondLeaf ? tileY + 1.0f : tileY)) : (tileY + (hingeSouth ? 1.0f : 0.0f));
                        placementOpen = InteractiveLayer.createHingeSwungPlacement(placementClosed, hingeX, hingeY, north, hingeEast, hingeSouth, isSecondLeaf);
                    }

                    Texture texClosed = InteractiveLayer.getClosedDoorTexture(door);

                    InteractiveLayer.getInstance().registerDoor(door, tileX, tileY, floorZ, north, wide, garage,
                            isDouble, isSecondLeaf, hingeEast, hingeSouth, hasNeighborNeg, hasNeighborPos,
                            placementClosed, placementOpen, texClosed);

                    if (door instanceof IsoDoor && ((IsoDoor) door).HasCurtains() != null) {
                        InteractiveLayer.getInstance().registerCurtain(door, tileX, tileY, floorZ, north, 1, null, null, null);
                    }

                    if (list != null && list.size() > keepBoxes) {
                        for (int i = list.size() - 1; i >= keepBoxes; i--) {
                            Geometry.Box b = list.get(i);
                            if (!b.solid()) list.remove(i);
                        }
                    }

                    if (faces != null && faces.size() > keepFaces) {
                        faces.subList(keepFaces, faces.size()).clear();
                    }

                    if (assets != null && assets.size() > snap.assetsStart) {
                        assets.subList(snap.assetsStart, assets.size()).clear();
                    }

                    if (ab != null && ab.size() > snap.abStart) {
                        ab.subList(snap.abStart, ab.size()).clear();
                    }
                }

                // =============================================================
                // 2. WINDOWS: Decouple 3D Frame & Suppress Broken Sprites
                // =============================================================
                if (window != null) {
                    Object placementIntact = null;
                    Object placementSmashed = null;

                    if (assets != null && assets.size() > snap.assetsStart) {
                        for (int idx = snap.assetsStart; idx < assets.size(); idx++) {
                            Object p = assets.get(idx);
                            if (p != null) {
                                placementIntact = p;
                                break;
                            }
                        }
                    }

                    if (ac != null && mAcVisual != null && window.getSprite() != null) {
                        String sprName = window.getSprite().getName();
                        float rot = north ? 0.0f : 90.0f;

                        if (placementIntact == null) {
                            placementIntact = InteractiveLayer.resolveVisualPlacement(ac, mAcVisual, sprName, window,
                                    (float) tileX, (float) tileY, floorZ, rot, "closed", "default");
                        }

                        placementSmashed = InteractiveLayer.resolveVisualPlacement(ac, mAcVisual, sprName, window,
                                (float) tileX, (float) tileY, floorZ, rot, "smashed", "open");
                    }

                    Texture tex = InteractiveLayer.getTexture(window);

                    int wallBoxesEnd = snap.boxesStart + 4;
                    List<Geometry.Box> windowFrameBoxes = new ArrayList<>();
                    if (list != null && list.size() > wallBoxesEnd) {
                        windowFrameBoxes.addAll(list.subList(wallBoxesEnd, list.size()));
                        list.subList(wallBoxesEnd, list.size()).clear();
                    }

                    IsoObject structural = (mStructural != null) ? (IsoObject) mStructural.invoke(null, sq, north) : null;
                    int wallFacesCount = (structural != null && InteractiveLayer.getTexture(structural) != null) ? 5 : 0;
                    int wallFacesEnd = snap.facesStart + wallFacesCount;

                    if (faces != null && faces.size() > wallFacesEnd) {
                        faces.subList(wallFacesEnd, faces.size()).clear();
                    }

                    if (assets != null && assets.size() > snap.assetsStart) {
                        assets.subList(snap.assetsStart, assets.size()).clear();
                    }

                    InteractiveLayer.getInstance().registerWindow(window, tileX, tileY, floorZ, north,
                            placementIntact, placementSmashed, tex, windowFrameBoxes);
                }

            } catch (Throwable t) {
                System.err.println("[PZ3DFix] Error in AddBoundaryHook exit: " + t.getMessage());
            }
        }
    }

    // =========================================================================
    // Dynamic Collision Injection
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.WorldMirror", methodName = "collision")
    public static class WorldMirrorCollisionHook {
        @Patch.OnExit
        public static void exit(
                @Patch.This WorldMirror mirror,
                @Patch.Return(readOnly = false) List<Geometry.Box> collisionBoxes
        ) {
            if (collisionBoxes != null && mirror != null) {
                try {
                    WorldMirror.Scene scene = mirror.scene();
                    if (scene != null) {
                        List<Geometry.Box> mutableList = new ArrayList<>(collisionBoxes);
                        InteractiveLayer.getInstance().injectDynamicCollisions(mutableList, scene.ox(), scene.oy());
                        collisionBoxes = mutableList;
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    // =========================================================================
    // Native Render Hooks
    // =========================================================================

    @Patch(className = "com.pavelvoronin.pz3d.TreeRenderer", methodName = "draw")
    public static class NativeOpaqueDrawHook {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) WorldMirror.Scene scene,
                @Patch.Argument(1) Matrix4f vp,
                @Patch.Argument(2) float eyeX,
                @Patch.Argument(3) float eyeY,
                @Patch.Argument(4) float eyeZ
        ) {
            InteractiveLayer.getInstance().renderOpaque(vp, eyeX, eyeY, eyeZ);
        }
    }

    @Patch(className = "com.pavelvoronin.pz3d.VehicleRenderer", methodName = "glass")
    public static class NativeTransparentDrawHook {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(1) Matrix4f vp,
                @Patch.Argument(2) float eyeX,
                @Patch.Argument(3) float eyeY,
                @Patch.Argument(4) float eyeZ
        ) {
            InteractiveLayer.getInstance().renderTransparent(vp, eyeX, eyeY, eyeZ);
        }
    }
}