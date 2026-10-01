package com.max.pz3dfix;

import me.zed_0xff.zombie_buddy.Patch;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

@Patch(className = "com.pavelvoronin.pz3d.TreeLod", methodName = "refresh")
public class Patch_PZ3D_TreeHorizon {

    public static Field fSettings;
    public static Constructor<?> settingsCtor;
    public static boolean reflectionOk = false;

    // Horizon distance in meters for tree impostors (matches camera far plane)
    public static final int TREE_HORIZON_DISTANCE = 420;

    static {
        try {
            Class<?> treeLodClass = Class.forName("com.pavelvoronin.pz3d.TreeLod");
            Class<?> settingsClass = Class.forName("com.pavelvoronin.pz3d.TreeLod$Settings");

            fSettings = treeLodClass.getDeclaredField("settings");
            fSettings.setAccessible(true);

            settingsCtor = settingsClass.getDeclaredConstructor(
                    int.class, int.class, int.class, int.class, int.class
            );
            settingsCtor.setAccessible(true);

            reflectionOk = true;

            // Apply immediately on classload
            applyHorizonLod();
            System.out.println("[PZ3DFix] TreeLod un-capped to " + TREE_HORIZON_DISTANCE + "m horizon.");
        } catch (Throwable t) {
            System.err.println("[PZ3DFix] Failed to initialize TreeHorizon patch:");
            t.printStackTrace();
        }
    }

    public static void applyHorizonLod() {
        if (!reflectionOk) return;
        try {
            // mode = 2, first = 14m, second = 30m, third = 420m, bandWidth = 25m
            Object extendedSettings = settingsCtor.newInstance(2, 14, 30, TREE_HORIZON_DISTANCE, 25);
            fSettings.set(null, extendedSettings);
        } catch (Throwable ignored) {
        }
    }

    @Patch.OnExit
    public static void exit() {
        applyHorizonLod();
    }
}