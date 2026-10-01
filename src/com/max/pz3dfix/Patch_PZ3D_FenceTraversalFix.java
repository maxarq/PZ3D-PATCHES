package com.max.pz3dfix;

import com.pavelvoronin.pz3d.Main;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;

import java.lang.reflect.Method;

public class Patch_PZ3D_FenceTraversalFix {

    public static Method mLease;
    public static Method mClear;
    public static boolean reflectionOk = false;

    public static volatile long lastLeaseTime = 0L;
    public static volatile IsoPlayer leasedPlayer = null;
    public static boolean inRelease = false;

    static {
        try {
            Class<?> niClass = Class.forName("com.pavelvoronin.pz3d.NativeInteraction");
            mLease = niClass.getDeclaredMethod("lease", IsoPlayer.class, boolean.class);
            mLease.setAccessible(true);

            mClear = niClass.getDeclaredMethod("clear");
            mClear.setAccessible(true);

            reflectionOk = true;
            System.out.println("[PZ3DFix] FenceTraversalFix reflection initialized successfully.");
        } catch (Throwable t) {
            System.err.println("[PZ3DFix] Failed to initialize reflection for FenceTraversalFix:");
            t.printStackTrace();
        }
    }

    /**
     * Safely releases any active NativeInteraction lease and restores character movement.
     */
    public static void releaseLease(String reason) {
        if (inRelease) {
            return;
        }
        inRelease = true;
        try {
            IsoPlayer player = IsoPlayer.getInstance();
            if (player != null) {
                player.setBlockMovement(false);
                if (reflectionOk && mLease != null) {
                    mLease.invoke(null, player, false);
                }
            }

            if (reflectionOk && mClear != null) {
                mClear.invoke(null);
            }

            // Also invoke PZ3D's public exposer method to ensure full state alignment
            Main.interactionAction(false);
            leasedPlayer = null;

            System.out.println("[PZ3DFix] Released NativeInteraction lease (reason: " + reason + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            inRelease = false;
        }
    }

    /**
     * Checks if the player is actively engaged in a legitimate interaction.
     */
    public static boolean isLegitimateInteraction(IsoPlayer player) {
        if (player == null) return false;

        // 1. Actively in PZ3D obstacle traversal (vaulting / window climb)
        if (Main.interactionTraversal()) {
            return true;
        }

        // 2. Currently playing an engine climbing / vaulting animation state
        if (player.getCurrentState() != null) {
            String stateName = player.getCurrentState().getClass().getSimpleName();
            if (stateName.contains("Climb") || stateName.contains("WindowState") || stateName.contains("Fence") || stateName.contains("Wall") || stateName.contains("Vault")) {
                return true;
            }
        }

        // 3. Dragging a corpse or grappling a body
        if (player.isDraggingCorpse() || player.isGrappling() || Main.interactionBodyTransition()) {
            return true;
        }

        // 4. Executing an in-game timed action (eating, reading, bandaging, etc.)
        if (player.hasTimedActions()) {
            return true;
        }

        return false;
    }

    // =========================================================================
    // 1. Hook Traversal.clear()
    // Directly un-leases NativeInteraction the exact moment "Traversal end" prints.
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.Traversal", methodName = "clear")
    public static class TraversalClearHook {
        @Patch.OnExit
        public static void exit() {
            releaseLease("Traversal.clear");
        }
    }

    // =========================================================================
    // 2. Hook NativeInteraction.lease(IsoPlayer, boolean)
    // Records lease timestamp for timeout tracking.
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.NativeInteraction", methodName = "lease")
    public static class LeaseTrackHook {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) IsoPlayer player,
                @Patch.Argument(1) boolean lease
        ) {
            if (lease) {
                lastLeaseTime = System.currentTimeMillis();
                leasedPlayer = player;
            } else {
                leasedPlayer = null;
            }
        }
    }

    // =========================================================================
    // 3. Hook NativeInteraction.ownsMotion(IsoGameCharacter)
    // Active safety net: if the lease is orphaned and has been held for >1500ms
    // without any active interaction, auto-release it.
    // We do NOT check isMoving here so walking with 'W' into a vault is never blocked.
    // =========================================================================
    @Patch(className = "com.pavelvoronin.pz3d.NativeInteraction", methodName = "ownsMotion")
    public static class OwnsMotionHook {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) IsoGameCharacter character,
                @Patch.Return(readOnly = false) boolean owns
        ) {
            if (!owns || !(character instanceof IsoPlayer)) {
                return;
            }

            IsoPlayer player = (IsoPlayer) character;

            // If the player is legitimately in an action, keep ownership intact
            if (isLegitimateInteraction(player)) {
                return;
            }

            // Only release after 1.5 seconds if no legitimate interaction ever started/remained
            long elapsed = System.currentTimeMillis() - lastLeaseTime;
            if (elapsed > 1500L) {
                owns = false;
                releaseLease("orphaned ownsMotion timeout (elapsed=" + elapsed + "ms)");
            }
        }
    }
}