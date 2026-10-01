package com.max.pz3dfix;

import me.zed_0xff.zombie_buddy.Patch;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import zombie.characters.IsoPlayer;
import zombie.core.Color;
import zombie.iso.IsoGridSquare;
import zombie.iso.weather.ClimateColorInfo;
import zombie.iso.weather.ClimateManager;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@Patch(className = "com.pavelvoronin.pz3d.Sky", methodName = "draw")
public class Patch_PZ3D_Sky {

    public static Field fBo;
    public static boolean reflectionDone = false;

    static {
        try {
            Class<?> skyClass = Class.forName("com.pavelvoronin.pz3d.Sky");
            try {
                fBo = skyClass.getDeclaredField("N");
            } catch (NoSuchFieldException e) {
                try {
                    fBo = skyClass.getDeclaredField("bo");
                } catch (NoSuchFieldException e2) {
                    for (Field f : skyClass.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers())
                                && f.getType() == int.class
                                && !f.getName().equals("SUN_SCALE")
                                && !f.getName().equals("MOON_SCALE")) {
                            fBo = f;
                            break;
                        }
                    }
                }
            }
            if (fBo != null) fBo.setAccessible(true);
            reflectionDone = true;
        } catch (Throwable t) {
            reflectionDone = true;
        }
    }

    @Patch.OnEnter
    public static void enter() {
        if (fBo == null) return;

        try {
            // Re-read updated camera and scene origin after uploads.advance()
            Patch_PZ3D_AtmosphereUpload.updateFromCurrentFrame();

            int skyProgram = fBo.getInt(null);
            if (skyProgram <= 0) return;

            ClimateManager clim = ClimateManager.getInstance();
            if (clim == null) return;

            float rawFog = clim.getFogIntensity();
            float visualFogIntensity = (float) Math.pow(Math.max(0.0f, Math.min(1.0f, rawFog)), 0.65f);

            ClimateColorInfo colFogInfo = clim.getColorNewFog();
            if (colFogInfo == null) colFogInfo = clim.getColFogNew();
            Color fogColor = (colFogInfo != null && colFogInfo.getExterior() != null)
                    ? colFogInfo.getExterior()
                    : new Color(0.85f, 0.88f, 0.92f, 1.0f);

            float rainIntensity = clim.getRainIntensity();
            float timeSec = (float) ((System.currentTimeMillis() % 10000000L) / 1000.0);

            IsoPlayer player = IsoPlayer.getInstance();
            boolean isInside = false;
            if (player != null && player.getCurrentSquare() != null) {
                IsoGridSquare sq = player.getCurrentSquare();
                isInside = sq.isInARoom() || sq.haveRoof;
            }

            int prevProgram = GL11.glGetInteger(35725);

            GL20.glUseProgram(skyProgram);

            int locSkyIntens = GL20.glGetUniformLocation(skyProgram, "u_fogIntensity");
            if (locSkyIntens >= 0) GL20.glUniform1f(locSkyIntens, visualFogIntensity);

            int locSkyCol = GL20.glGetUniformLocation(skyProgram, "u_fogColor");
            if (locSkyCol >= 0) GL20.glUniform3f(locSkyCol, fogColor.r, fogColor.g, fogColor.b);

            int locRain = GL20.glGetUniformLocation(skyProgram, "u_rainIntensity");
            if (locRain >= 0) GL20.glUniform1f(locRain, rainIntensity);

            int locTime = GL20.glGetUniformLocation(skyProgram, "u_time");
            if (locTime >= 0) GL20.glUniform1f(locTime, timeSec);

            int locInside = GL20.glGetUniformLocation(skyProgram, "u_cameraInside");
            if (locInside >= 0) GL20.glUniform1i(locInside, isInside ? 1 : 0);

            if (prevProgram > 0 && prevProgram != skyProgram) {
                GL20.glUseProgram(prevProgram);
            }
        } catch (Throwable ignored) {
        }
    }

    @Patch.OnExit
    public static void exit(
            @Patch.Argument(0) Object lighting,
            @Patch.Argument(1) Matrix4f viewProjection,
            @Patch.Argument(2) Vector3f eye
    ) {
        if (viewProjection == null || eye == null) return;

        try {
            IsoPlayer player = IsoPlayer.getInstance();
            if (player == null) return;

            // Re-read current scene origin so viewProjection and meshOrigin are in lockstep
            Patch_PZ3D_AtmosphereUpload.updateFromCurrentFrame();

            float sceneOx = Patch_PZ3D_AtmosphereUpload.hasSceneOrigin
                    ? Patch_PZ3D_AtmosphereUpload.sceneOx
                    : (float) Math.floor(player.getX() / 16.0f) * 16.0f;
            float sceneOy = Patch_PZ3D_AtmosphereUpload.hasSceneOrigin
                    ? Patch_PZ3D_AtmosphereUpload.sceneOy
                    : (float) Math.floor(player.getY() / 16.0f) * 16.0f;
            float sceneOz = Patch_PZ3D_AtmosphereUpload.hasSceneOrigin
                    ? Patch_PZ3D_AtmosphereUpload.sceneOz
                    : (float) Math.floor(player.getZ()) * 2.44949f;

            FarTerrainRenderer.getInstance().render(
                    viewProjection,
                    sceneOx, sceneOy, sceneOz,
                    eye.x, eye.y, eye.z
            );
        } catch (Throwable t) {
            System.err.println("[PZ3DFix] Error in FarTerrainRenderer exit hook: " + t.getMessage());
        }
    }
}