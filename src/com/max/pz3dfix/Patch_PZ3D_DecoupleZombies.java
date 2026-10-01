package com.max.pz3dfix;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.objects.IsoDeadBody;
import zombie.network.id.IIdentifiable;
import zombie.network.id.ObjectIDType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Patch_PZ3D_DecoupleZombies {

    // =========================================================================
    // 1. Decouple Live Zombies & Corpses from Chunk Meshing / StreamFade Gating
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.StreamFade", methodName = "actorReady")
    public static class ForceActorReadyHook {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean ready) {
            // Live zombies and corpses are immediately ready in 3D
            // without waiting for the chunk beneath them to finish crawling/meshing
            ready = true;
        }
    }

    // =========================================================================
    // 2. Decouple Dead Bodies from WorldMirror$Cached: Feed Directly from Native Registry
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.Corpses", methodName = "update")
    public static class NativeCorpsesHook {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(value = 0, readOnly = false) List<IsoDeadBody> bodies,
                @Patch.Argument(1) float playerX,
                @Patch.Argument(2) float playerY
        ) {
            try {
                Collection<IIdentifiable> objects = ObjectIDType.DeadBody.getObjects();
                if (objects == null || objects.isEmpty()) return;

                List<IsoDeadBody> nativeBodies = new ArrayList<>(64);
                for (IIdentifiable identifiable : objects) {
                    if (identifiable instanceof IsoDeadBody) {
                        IsoDeadBody body = (IsoDeadBody) identifiable;
                        if (body.getSquare() != null && !body.isAnimal()) {
                            float dx = body.getX() - playerX;
                            float dy = body.getY() - playerY;
                            // Within 80 meters (approx. 10 PZ3D chunks)
                            if ((dx * dx) + (dy * dy) <= 6400.0f) {
                                nativeBodies.add(body);
                            }
                        }
                    }
                }

                // Replace PZ3D's chunk-crawled list with the authoritative native list
                bodies = nativeBodies;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    // =========================================================================
    // 3. Stop Item Drops & Deaths from Aborting In-Progress Building Captures
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.WorldMirror", methodName = "dropped")
    public static class PreventDropAbortHook {
        private static Field fBv = null;
        private static boolean bvInit = false;
        private static Object savedBv = null;

        @Patch.OnEnter
        public static void enter(@Patch.This Object mirror) {
            if (mirror != null) {
                try {
                    if (!bvInit) {
                        fBv = mirror.getClass().getDeclaredField("Bv");
                        fBv.setAccessible(true);
                        bvInit = true;
                    }
                    savedBv = fBv.get(mirror);
                } catch (Throwable ignored) {
                }
            }
        }

        @Patch.OnExit
        public static void exit(@Patch.This Object mirror) {
            if (mirror != null && savedBv != null && fBv != null) {
                try {
                    // If dropped() reset Bv to null, restore it so active chunk capture continues
                    if (fBv.get(mirror) == null) {
                        fBv.set(mirror, savedBv);
                    }
                } catch (Throwable ignored) {
                } finally {
                    savedBv = null;
                }
            }
        }
    }
}