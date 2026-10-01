package com.max.pz3dfix;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.textures.Texture;
import zombie.iso.IsoObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Patch_PZ3D_FoliageCross {

    public static Class<?> quadClass;
    public static Constructor<?> quadCtor;
    public static Method mX0, mY0, mX1, mY1, mZ, mHeight, mTex, mLayer, mVeg, mFoliage;
    public static boolean reflectionOk = false;

    // 1/6 of a tile in world Z coordinates (~0.1667f)
    public static final float Z_OFFSET = 1.0f / 6.0f;

    static {
        try {
            quadClass = Class.forName("com.pavelvoronin.pz3d.Ground$Quad");
            Class<?> profileClass = Class.forName("com.pavelvoronin.pz3d.Foliage$Profile");

            quadCtor = quadClass.getDeclaredConstructor(
                    float.class, float.class, float.class, float.class, float.class, float.class,
                    Texture.class, int.class, boolean.class, profileClass
            );
            quadCtor.setAccessible(true);

            mX0 = quadClass.getDeclaredMethod("x0");
            mX0.setAccessible(true);
            mY0 = quadClass.getDeclaredMethod("y0");
            mY0.setAccessible(true);
            mX1 = quadClass.getDeclaredMethod("x1");
            mX1.setAccessible(true);
            mY1 = quadClass.getDeclaredMethod("y1");
            mY1.setAccessible(true);
            mZ = quadClass.getDeclaredMethod("z");
            mZ.setAccessible(true);
            mHeight = quadClass.getDeclaredMethod("height");
            mHeight.setAccessible(true);
            mTex = quadClass.getDeclaredMethod("texture");
            mTex.setAccessible(true);
            mLayer = quadClass.getDeclaredMethod("layer");
            mLayer.setAccessible(true);
            mVeg = quadClass.getDeclaredMethod("vegetation");
            mVeg.setAccessible(true);
            mFoliage = quadClass.getDeclaredMethod("foliage");
            mFoliage.setAccessible(true);

            reflectionOk = true;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void processQuads(List<Object> list, int startIndex) {
        if (!reflectionOk || list == null || startIndex >= list.size()) return;

        try {
            int currentSize = list.size();
            List<Object> crossToAdd = new ArrayList<>();

            for (int i = startIndex; i < currentSize; i++) {
                Object q = list.get(i);

                float x0 = (float) mX0.invoke(q);
                float y0 = (float) mY0.invoke(q);
                float x1 = (float) mX1.invoke(q);
                float y1 = (float) mY1.invoke(q);

                // Skip if already a cross-quad
                if (Math.abs(x1 - x0) < 0.001f) {
                    continue;
                }

                float cx = (x0 + x1) * 0.5f;
                float cy = (y0 + y1) * 0.5f;
                float dx = (x1 - x0) * 0.5f;
                float dy = (y1 - y0) * 0.5f;

                // Perpendicular 90-degree cross
                float nx0 = cx + dy;
                float ny0 = cy - dx;
                float nx1 = cx - dy;
                float ny1 = cy + dx;

                float zVal = (float) mZ.invoke(q);
                float loweredZ = zVal - Z_OFFSET; // Move down 1/6 of a tile

                float height = (float) mHeight.invoke(q);
                Texture tex = (Texture) mTex.invoke(q);
                int layer = (int) mLayer.invoke(q);
                boolean veg = (boolean) mVeg.invoke(q);
                Object foliage = mFoliage.invoke(q);

                // Replace original quad with lowered original quad
                Object origLowered = quadCtor.newInstance(x0, y0, x1, y1, loweredZ, height, tex, layer, veg, foliage);
                list.set(i, origLowered);

                // Add lowered perpendicular cross-quad
                Object crossLowered = quadCtor.newInstance(nx0, ny0, nx1, ny1, loweredZ, height, tex, layer, veg, foliage);
                crossToAdd.add(crossLowered);
            }

            list.addAll(crossToAdd);
        } catch (Throwable ignored) {
        }
    }

    // 1. Tell PZ3D that d_generic is upright vegetation (no overloads)
    @Patch(className = "com.pavelvoronin.pz3d.Vegetation", methodName = "uprightName")
    public static class UprightNamePatch {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) String str,
                @Patch.Return(readOnly = false) boolean result
        ) {
            if (!result && str != null && str.startsWith("d_generic")) {
                result = true;
            }
        }
    }

    // 2. Hook Vegetation.capture (no overloads)
    @Patch(className = "com.pavelvoronin.pz3d.Vegetation", methodName = "capture")
    public static class VegetationCapturePatch {
        public static int sizeBefore = 0;

        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) List<Object> list) {
            sizeBefore = (list != null) ? list.size() : 0;
        }

        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) List<Object> list,
                @Patch.Argument(1) IsoObject isoObject
        ) {
            processQuads(list, sizeBefore);
        }
    }

    // 3. Hook Vegetation.floorAttachments (no overloads)
    @Patch(className = "com.pavelvoronin.pz3d.Vegetation", methodName = "floorAttachments")
    public static class VegetationFloorPatch {
        public static int sizeBefore = 0;

        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) List<Object> list) {
            sizeBefore = (list != null) ? list.size() : 0;
        }

        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) List<Object> list,
                @Patch.Argument(1) IsoObject isoObject
        ) {
            processQuads(list, sizeBefore);
        }
    }
}