package com.max.pz3dfix;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.*;

public class Patch_PZ3D_EventDrivenChunks {

    /**
     * Called ONLY for true structural changes (sledgehammering a wall, building carpentry floors/walls).
     */
    public static void markChunkDirty(int tileX, int tileY) {
        Patch_PZ3D_ToggleSpeedup.triggerImmediateChunkReload(tileX, tileY);
    }

    public static void markSquareDirty(IsoGridSquare sq) {
        if (sq != null) {
            markChunkDirty(sq.getX(), sq.getY());
        }
    }

    // =========================================================================
    // 1. DOORS (IsoDoor) - 0.0 ms: Updates InteractiveLayer immediately, NO CHUNK REBUILD
    // =========================================================================
    @Patch(className = "zombie.iso.objects.IsoDoor", methodName = "ToggleDoorActual")
    public static class DoorToggleHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoDoor door, @Patch.Argument(0) IsoGameCharacter chr) {
            if (door != null) {
                InteractiveLayer.getInstance().onDoorToggled(door);
            }
        }
    }

    @Patch(className = "zombie.iso.objects.IsoDoor", methodName = "destroy")
    public static class DoorDestroyHook {
        @Patch.OnEnter
        public static void enter(@Patch.This IsoDoor door) {
            if (door != null) {
                InteractiveLayer.getInstance().onDoorToggled(door);
            }
        }
    }

    @Patch(className = "zombie.iso.objects.IsoDoor", methodName = "setCurtainOpen")
    public static class DoorCurtainHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoDoor door, @Patch.Argument(0) boolean open) {
            if (door != null) {
                InteractiveLayer.getInstance().onCurtainToggled(door);
            }
        }
    }

    @Patch(className = "zombie.iso.objects.IsoDoor", methodName = "addSheet")
    public static class DoorAddSheetHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoDoor door, @Patch.Argument(0) boolean inside, @Patch.Argument(1) IsoGameCharacter chr) {
            if (door != null) {
                InteractiveLayer.getInstance().onCurtainToggled(door);
            }
        }
    }

    @Patch(className = "zombie.iso.objects.IsoDoor", methodName = "removeSheet")
    public static class DoorRemoveSheetHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoDoor door, @Patch.Argument(0) IsoGameCharacter chr) {
            if (door != null) {
                // FIXED: Do NOT remove the door! Only update curtain state on the door.
                InteractiveLayer.getInstance().onCurtainToggled(door);
            }
        }
    }

    // =========================================================================
    // 2. WINDOWS (IsoWindow) - 0.0 ms: Updates InteractiveLayer immediately, NO CHUNK REBUILD
    // =========================================================================
    @Patch(className = "zombie.iso.objects.IsoWindow", methodName = "ToggleWindow")
    public static class WindowToggleHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoWindow window, @Patch.Argument(0) IsoGameCharacter chr) {
            if (window != null) {
                InteractiveLayer.getInstance().onWindowChanged(window);
            }
        }
    }

    @Patch(className = "zombie.iso.objects.IsoWindow", methodName = "smashWindow")
    public static class WindowSmashHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoWindow window, @Patch.Argument(0) boolean bRemote, @Patch.Argument(1) boolean doAlarm) {
            if (window != null) {
                InteractiveLayer.getInstance().onWindowChanged(window);
            }
        }
    }

    @Patch(className = "zombie.iso.objects.IsoWindow", methodName = "setGlassRemoved")
    public static class WindowGlassRemovedHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoWindow window, @Patch.Argument(0) boolean removed) {
            if (window != null) {
                InteractiveLayer.getInstance().onWindowChanged(window);
            }
        }
    }

    // =========================================================================
    // 3. CURTAINS (IsoCurtain) - 0.0 ms: Updates InteractiveLayer immediately, NO CHUNK REBUILD
    // =========================================================================
    @Patch(className = "zombie.iso.objects.IsoCurtain", methodName = "ToggleDoor")
    public static class CurtainToggleHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoCurtain curtain, @Patch.Argument(0) IsoGameCharacter chr) {
            if (curtain != null) {
                InteractiveLayer.getInstance().onCurtainToggled(curtain);
            }
        }
    }

    @Patch(className = "zombie.iso.objects.IsoCurtain", methodName = "ToggleDoorSilent")
    public static class CurtainToggleSilentHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoCurtain curtain) {
            if (curtain != null) {
                InteractiveLayer.getInstance().onCurtainToggled(curtain);
            }
        }
    }

    @Patch(className = "zombie.iso.objects.IsoCurtain", methodName = "removeSheet")
    public static class CurtainRemoveSheetHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoCurtain curtain, @Patch.Argument(0) IsoGameCharacter chr) {
            if (curtain != null) {
                InteractiveLayer.getInstance().removeObject(curtain);
            }
        }
    }

    // =========================================================================
    // 4. THUMPABLES (Player-built doors & thumpables)
    // =========================================================================
    @Patch(className = "zombie.iso.objects.IsoThumpable", methodName = "ToggleDoorActual")
    public static class ThumpableToggleDoorHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoThumpable thump, @Patch.Argument(0) IsoGameCharacter chr) {
            if (thump != null && thump.isDoor()) {
                InteractiveLayer.getInstance().onDoorToggled(thump);
            }
        }
    }

    @Patch(className = "zombie.iso.objects.IsoThumpable", methodName = "destroy")
    public static class ThumpableDestroyHook {
        @Patch.OnEnter
        public static void enter(@Patch.This IsoThumpable thump) {
            if (thump != null) {
                if (thump.isDoor()) {
                    InteractiveLayer.getInstance().onDoorToggled(thump);
                } else {
                    markSquareDirty(thump.getSquare());
                }
            }
        }
    }

    // =========================================================================
    // 5. STRUCTURAL CHANGES (Walls sledgehammered, Carpentry built)
    // =========================================================================
    @Patch(className = "zombie.iso.IsoGridSquare", methodName = "AddTileObject")
    public static class SquareAddTileObjectHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoGridSquare sq, @Patch.Argument(0) IsoObject obj, @Patch.Argument(1) int index) {
            if (sq != null && obj != null && !(obj instanceof IsoDoor) && !(obj instanceof IsoWindow) && !(obj instanceof IsoCurtain)) {
                markSquareDirty(sq);
            }
        }
    }

    @Patch(className = "zombie.iso.IsoGridSquare", methodName = "AddSpecialObject")
    public static class SquareAddSpecialObjectHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoGridSquare sq, @Patch.Argument(0) IsoObject obj, @Patch.Argument(1) int index) {
            if (sq != null && obj != null && !(obj instanceof IsoDoor) && !(obj instanceof IsoWindow) && !(obj instanceof IsoCurtain)) {
                markSquareDirty(sq);
            }
        }
    }

    @Patch(className = "zombie.iso.IsoGridSquare", methodName = "RemoveTileObject")
    public static class SquareRemoveTileObjectHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoGridSquare sq, @Patch.Argument(0) IsoObject obj, @Patch.Argument(1) boolean safelyRemove) {
            if (sq != null && obj != null && !(obj instanceof IsoDoor) && !(obj instanceof IsoWindow) && !(obj instanceof IsoCurtain)) {
                markSquareDirty(sq);
            }
        }
    }

    @Patch(className = "zombie.iso.IsoGridSquare", methodName = "addFloor")
    public static class SquareAddFloorHook {
        @Patch.OnExit
        public static void exit(@Patch.This IsoGridSquare sq, @Patch.Argument(0) String sprite) {
            if (sq != null) {
                markSquareDirty(sq);
            }
        }
    }
}