package com.max.pz3dfix;

import com.pavelvoronin.pz3d.NativeLocomotion;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.characters.component.CharacterInputComponent;

public class Patch_PZ3D_Sprint {

    // 1. Hook Sprint (Left Alt by default)
    @Patch(className = "zombie.characters.component.CharacterInputComponent", methodName = "isSprintButtonDown")
    public static class SprintHook {
        @Patch.OnExit
        public static void exit(
                @Patch.This CharacterInputComponent component,
                @Patch.Return(readOnly = false) boolean isSprint
        ) {
            if (!isSprint || !Patch_PZ3D_Strafe.reflectionOk) return;

            try {
                IsoGameCharacter character = (IsoGameCharacter) component.tryGetECSOwnerEntityAs(IsoGameCharacter.class);
                if (character == null || !NativeLocomotion.firstPerson(character)) return;

                Object sample = Patch_PZ3D_Strafe.mLatest.invoke(null);
                if (sample == null) return;

                float sampleForward = (float) Patch_PZ3D_Strafe.mForward.invoke(sample);

                // Only allow sprinting if moving forward (W, W+A, W+D)
                if (sampleForward <= 0.0f) {
                    isSprint = false;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    // 2. Hook Run (Shift by default)
    @Patch(className = "zombie.characters.component.CharacterInputComponent", methodName = "isRunButtonDown")
    public static class RunHook {
        @Patch.OnExit
        public static void exit(
                @Patch.This CharacterInputComponent component,
                @Patch.Return(readOnly = false) boolean isRun
        ) {
            if (!isRun || !Patch_PZ3D_Strafe.reflectionOk) return;

            try {
                IsoGameCharacter character = (IsoGameCharacter) component.tryGetECSOwnerEntityAs(IsoGameCharacter.class);
                if (character == null || !NativeLocomotion.firstPerson(character)) return;

                Object sample = Patch_PZ3D_Strafe.mLatest.invoke(null);
                if (sample == null) return;

                float sampleForward = (float) Patch_PZ3D_Strafe.mForward.invoke(sample);

                // Only allow running if moving forward (W, W+A, W+D)
                if (sampleForward <= 0.0f) {
                    isRun = false;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}