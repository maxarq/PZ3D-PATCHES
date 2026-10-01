package com.max.pz3dfix;

import com.pavelvoronin.pz3d.WorldMirror;
import me.zed_0xff.zombie_buddy.Patch;
import org.lwjgl.opengl.GL20;
import zombie.characters.IsoPlayer;
import zombie.core.Color;
import zombie.iso.IsoGridSquare;
import zombie.iso.weather.ClimateColorInfo;
import zombie.iso.weather.ClimateManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Patch_PZ3D_AtmosphereUpload {

    public static float camX = 0f;
    public static float camY = 0f;
    public static float camZ = 0f;
    public static boolean hasCam = false;

    // True PZ3D scene origin captured from Renderer$Frame.scene
    public static float sceneOx = 0f;
    public static float sceneOy = 0f;
    public static float sceneOz = 0f;
    public static boolean hasSceneOrigin = false;

    public static Object currentFrame = null;

    private static Field fX, fY, fZ, fScene;
    private static Method mOx, mOy, mOz;
    private static boolean reflectionInit = false;

    private static void ensureReflectionInit(Object frame) {
        if (!reflectionInit && frame != null) {
            try {
                Class<?> cls = frame.getClass();
                fX = cls.getDeclaredField("x");
                fX.setAccessible(true);
                fY = cls.getDeclaredField("y");
                fY.setAccessible(true);
                fZ = cls.getDeclaredField("z");
                fZ.setAccessible(true);

                fScene = cls.getDeclaredField("scene");
                fScene.setAccessible(true);
            } catch (Throwable ignored) {
            }
            reflectionInit = true;
        }
    }

    public static void captureFrame(Object frame) {
        currentFrame = frame;
        ensureReflectionInit(frame);
        updateFromCurrentFrame();
    }

    /**
     * Re-reads scene and camera values from currentFrame.
     * Calling this at Sky.draw ensures we read the updated scene AFTER uploads.advance().
     */
    public static void updateFromCurrentFrame() {
        if (currentFrame == null) return;
        ensureReflectionInit(currentFrame);

        if (fX != null) {
            try {
                camX = fX.getFloat(currentFrame);
                camY = fY.getFloat(currentFrame);
                camZ = fZ.getFloat(currentFrame);
                hasCam = true;
            } catch (Throwable ignored) {
            }
        }

        if (fScene != null) {
            try {
                Object sceneObj = fScene.get(currentFrame);
                if (sceneObj != null) {
                    if (mOx == null) {
                        Class<?> sCls = sceneObj.getClass();
                        mOx = sCls.getMethod("ox");
                        mOy = sCls.getMethod("oy");
                        mOz = sCls.getMethod("oz");
                    }
                    sceneOx = (float) mOx.invoke(sceneObj);
                    sceneOy = (float) mOy.invoke(sceneObj);
                    sceneOz = (float) mOz.invoke(sceneObj);
                    hasSceneOrigin = true;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Patch(className = "com.pavelvoronin.pz3d.Renderer$Frame", methodName = "draw")
    public static class FrameHook {
        @Patch.OnEnter
        public static void enter(@Patch.This Object frame) {
            captureFrame(frame);
        }
    }

    @Patch(className = "com.pavelvoronin.pz3d.Enclosure", methodName = "upload")
    public static class EnclosureHook {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(0) int programId,
                @Patch.Argument(1) Object field,
                @Patch.Argument(2) WorldMirror.Scene scene
        ) {
            if (programId == 0 || scene == null) return;

            try {
                IsoPlayer player = IsoPlayer.getInstance();
                if (player == null) return;

                ClimateManager clim = ClimateManager.getInstance();
                if (clim == null) return;

                float rawFog = clim.getFogIntensity();
                float fogDensity = rawFog * 0.175f;

                ClimateColorInfo colFogInfo = clim.getColorNewFog();
                if (colFogInfo == null) {
                    colFogInfo = clim.getColFogNew();
                }
                Color fogColor = (colFogInfo != null && colFogInfo.getExterior() != null)
                        ? colFogInfo.getExterior()
                        : new Color(0.85f, 0.88f, 0.92f, 1.0f);

                updateFromCurrentFrame();

                float cX = hasCam ? camX : (player.getX() - scene.ox());
                float cY = hasCam ? camY : (player.getY() - scene.oy());
                float cZ = hasCam ? camZ : ((player.getZ() * 2.44949f) - scene.oz() + 1.6f);

                IsoGridSquare currentSquare = player.getCurrentSquare();
                boolean isInside = currentSquare != null && (currentSquare.isInARoom() || currentSquare.haveRoof);
                float rainIntensity = clim.getRainIntensity();
                float timeSec = (float) ((System.currentTimeMillis() % 10000000L) / 1000.0);

                int locCam = GL20.glGetUniformLocation(programId, "u_cameraPos");
                if (locCam >= 0) GL20.glUniform3f(locCam, cX, cY, cZ);

                int locFogDensity = GL20.glGetUniformLocation(programId, "u_fogDensity");
                if (locFogDensity >= 0) GL20.glUniform1f(locFogDensity, fogDensity);

                int locFogCol = GL20.glGetUniformLocation(programId, "u_fogColor");
                if (locFogCol >= 0) GL20.glUniform3f(locFogCol, fogColor.r, fogColor.g, fogColor.b);

                int locCamInside = GL20.glGetUniformLocation(programId, "u_cameraInside");
                if (locCamInside >= 0) GL20.glUniform1i(locCamInside, isInside ? 1 : 0);

                int locRain = GL20.glGetUniformLocation(programId, "u_rainIntensity");
                if (locRain >= 0) GL20.glUniform1f(locRain, rainIntensity);

                int locTime = GL20.glGetUniformLocation(programId, "u_time");
                if (locTime >= 0) GL20.glUniform1f(locTime, timeSec);

            } catch (Throwable ignored) {
            }
        }
    }
}