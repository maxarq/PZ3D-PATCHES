package com.max.pz3dfix;

import com.pavelvoronin.pz3d.NativeLocomotion;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;

import java.lang.reflect.Method;

@Patch(className = "zombie.characters.IsoGameCharacter", methodName = "isStrafing")
public class Patch_PZ3D_Strafe {

    public static Method mLatest;
    public static Method mForward;
    public static Method mStrafe;
    public static Method mAim;
    public static Method mCameraAttack;
    public static boolean reflectionOk = false;

    static {
        try {
            System.out.println("[PZ3DFix] Initializing reflection for PZ3D 0.4.0 internals...");

            Class<?> inputClass = Class.forName("com.pavelvoronin.pz3d.Input");
            mLatest = inputClass.getDeclaredMethod("latest");
            mLatest.setAccessible(true);

            Class<?> sampleClass = mLatest.getReturnType();
            mForward = sampleClass.getDeclaredMethod("forward");
            mForward.setAccessible(true);

            mStrafe = sampleClass.getDeclaredMethod("strafe");
            mStrafe.setAccessible(true);

            mAim = sampleClass.getDeclaredMethod("aim");
            mAim.setAccessible(true);

            Class<?> avatarClass = Class.forName("com.pavelvoronin.pz3d.NativeAvatar");
            mCameraAttack = avatarClass.getDeclaredMethod("cameraAttack");
            mCameraAttack.setAccessible(true);

            reflectionOk = true;
            System.out.println("[PZ3DFix] Strafe reflection initialized successfully.");
        } catch (Throwable t) {
            System.err.println("[PZ3DFix] Failed to initialize reflection for PZ3D:");
            t.printStackTrace();
        }
    }

    @Patch.OnExit
    public static void exit(
            @Patch.This IsoGameCharacter character,
            @Patch.Return(readOnly = false) boolean strafe
    ) {
        if (!reflectionOk || character == null) {
            return;
        }

        try {
            // Only modify strafing when in first-person mode.
            // Leaving strafe untouched in 3rd person / isometric prevents breaking vanilla aiming.
            if (!NativeLocomotion.firstPerson(character)) {
                return;
            }

            Object sample = mLatest.invoke(null);
            if (sample == null) {
                return;
            }

            boolean aim = (boolean) mAim.invoke(sample);
            boolean cameraAttack = (boolean) mCameraAttack.invoke(null);

            // 1. Combat readiness: Aiming or attacking -> always strafe
            if (aim || cameraAttack) {
                strafe = true;
                return;
            }

            float sampleForward = (float) mForward.invoke(sample);

            // 2. BACKWARDS (S): Always strafe!
            // Placing this before isRunning/isSprinting prevents the engine
            // from sprinting or running backwards at full speed.
            if (sampleForward < 0.0f) {
                strafe = true;
                return;
            }

            // 3. If running or sprinting forward (W, W+A, W+D) -> release strafe
            if (character.isRunning() || character.isSprinting()) {
                strafe = false;
                return;
            }

            // 4. Moving forward or laterally (A / D) at walking pace -> normal full walking speed
            strafe = false;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}