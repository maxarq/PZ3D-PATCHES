package com.max.pz3dfix;

import com.pavelvoronin.pz3d.WorldMirror;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import zombie.characters.IsoPlayer;
import zombie.core.Color;
import zombie.iso.weather.ClimateColorInfo;
import zombie.iso.weather.ClimateManager;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FarTerrainRenderer {

    private static final FarTerrainRenderer INSTANCE = new FarTerrainRenderer();
    public static FarTerrainRenderer getInstance() {
        return INSTANCE;
    }

    public static final int CELL_HORIZON_RADIUS = 3;
    public static final float FAR_MAX_DISTANCE = 1024.0f;
    public static final float BILLBOARD_MAX_DISTANCE = 500.0f;

    private final Map<Long, FarCellMesh> activeMeshes = new ConcurrentHashMap<>();
    private final Matrix4f worldMatrix = new Matrix4f();

    private static Field fFrameScene = null;
    private static boolean frameSceneInit = false;

    public void render(Matrix4f viewProjection, float sceneOx, float sceneOy, float sceneOz, float eyeX, float eyeY, float eyeZ) {
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null || player.isDead()) return;

        int playerCellX = Math.floorDiv((int) player.getX(), FarCellData.CELL_SIZE);
        int playerCellY = Math.floorDiv((int) player.getY(), FarCellData.CELL_SIZE);

        FarLotBinaryDecoder decoder = FarLotBinaryDecoder.getInstance();

        int uploadedThisFrame = 0;
        int requestedThisFrame = 0;

        for (int r = 0; r <= CELL_HORIZON_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;

                    int cx = playerCellX + dx;
                    int cy = playerCellY + dy;
                    long key = FarLotBinaryDecoder.packCell(cx, cy);

                    FarCellMesh mesh = activeMeshes.get(key);
                    if (mesh == null) {
                        FarCellData cellData = decoder.getCell(cx, cy);
                        if (cellData != null && cellData.ready) {
                            if (uploadedThisFrame < 1) {
                                mesh = new FarCellMesh(cx, cy);
                                mesh.build(cellData);
                                activeMeshes.put(key, mesh);
                                decoder.removeCell(cx, cy);
                                uploadedThisFrame++;
                            }
                        } else if (!decoder.isBusy() && requestedThisFrame < 1) {
                            decoder.requestCell(cx, cy);
                            requestedThisFrame++;
                        }
                    }
                }
            }
        }

        decoder.pruneDistant(playerCellX, playerCellY, CELL_HORIZON_RADIUS + 1);
        Iterator<Map.Entry<Long, FarCellMesh>> it = activeMeshes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, FarCellMesh> entry = it.next();
            long k = entry.getKey();
            int cx = (int) (k >> 32);
            int cy = (int) k;
            if (Math.abs(cx - playerCellX) > CELL_HORIZON_RADIUS + 1 || Math.abs(cy - playerCellY) > CELL_HORIZON_RADIUS + 1) {
                entry.getValue().release();
                it.remove();
            }
        }

        if (activeMeshes.isEmpty()) return;

        WorldMirror.Scene currentScene = null;
        if (Patch_PZ3D_AtmosphereUpload.currentFrame != null) {
            try {
                if (!frameSceneInit) {
                    fFrameScene = Patch_PZ3D_AtmosphereUpload.currentFrame.getClass().getDeclaredField("scene");
                    fFrameScene.setAccessible(true);
                    frameSceneInit = true;
                }
                if (fFrameScene != null) {
                    currentScene = (WorldMirror.Scene) fFrameScene.get(Patch_PZ3D_AtmosphereUpload.currentFrame);
                }
            } catch (Throwable ignored) {}
        }

        if (currentScene != null) {
            sceneOx = currentScene.ox();
            sceneOy = currentScene.oy();
            sceneOz = currentScene.oz();
        }

        FarCoverageTexture.getInstance().update(currentScene, player.getX(), player.getY());
        FarCoverageTexture.getInstance().bind(1);

        ClimateManager clim = ClimateManager.getInstance();
        float rawFog = (clim != null) ? clim.getFogIntensity() : 0.0f;
        float fogDensity = rawFog * 0.175f;

        ClimateColorInfo colFogInfo = (clim != null) ? clim.getColorNewFog() : null;
        if (colFogInfo == null && clim != null) colFogInfo = clim.getColFogNew();
        Color fogColor = (colFogInfo != null && colFogInfo.getExterior() != null)
                ? colFogInfo.getExterior()
                : new Color(0.85f, 0.88f, 0.92f, 1.0f);

        float rainIntensity = (clim != null) ? clim.getRainIntensity() : 0.0f;

        worldMatrix.set(viewProjection).translate(-sceneOx, -sceneOy, -sceneOz);
        FrustumIntersection frustum = new FrustumIntersection(worldMatrix);

        float camWorldX = eyeX + sceneOx;
        float camWorldY = eyeY + sceneOy;

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_CULL_FACE);

        // PASS 1: TERRAIN, WALLS, ROOFS (1024m)
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(1.0f, 2.0f);

        FarTileShader tileShader = FarTileShader.getInstance();
        tileShader.bind();

        try {
            tileShader.setViewProjection(viewProjection);
            tileShader.setMeshOrigin(sceneOx, sceneOy, sceneOz);
            tileShader.setCameraPos(eyeX, eyeY, eyeZ);
            tileShader.setSunDirection(0.4f, 0.3f, 0.86f);
            tileShader.setLighting(0.85f, 0.82f, 0.78f, 0.35f, 0.38f, 0.42f);
            tileShader.setFog(fogColor, fogDensity);
            tileShader.setRainIntensity(rainIntensity);
            tileShader.setPlayerChunk(FarCoverageTexture.getInstance().playerChunkX, FarCoverageTexture.getInstance().playerChunkY);
            tileShader.setTextureUnits(0, 1);

            for (FarCellMesh cellMesh : activeMeshes.values()) {
                cellMesh.renderTerrain(frustum, camWorldX, camWorldY, 0.0f, FAR_MAX_DISTANCE);
            }
        } finally {
            tileShader.unbind();
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }

        // PASS 2: BILLBOARDS (Trees, Tall Grass, Poles, Signs, Fixtures - 500m)
        FarTreeShader treeShader = FarTreeShader.getInstance();
        treeShader.bind();

        try {
            treeShader.setViewProjection(viewProjection);
            treeShader.setMeshOrigin(sceneOx, sceneOy, sceneOz);
            treeShader.setCameraPos(eyeX, eyeY, eyeZ);
            treeShader.setSunDirection(0.4f, 0.3f, 0.86f);
            treeShader.setLighting(0.85f, 0.82f, 0.78f, 0.35f, 0.38f, 0.42f);
            treeShader.setFog(fogColor, fogDensity);
            treeShader.setRainIntensity(rainIntensity);
            treeShader.setPlayerChunk(FarCoverageTexture.getInstance().playerChunkX, FarCoverageTexture.getInstance().playerChunkY);
            treeShader.setTextureUnits(0, 1);

            for (FarCellMesh cellMesh : activeMeshes.values()) {
                cellMesh.renderTrees(frustum, camWorldX, camWorldY, 0.0f, BILLBOARD_MAX_DISTANCE);
            }
        } finally {
            treeShader.unbind();
        }
    }

    public void clear() {
        for (FarCellMesh mesh : activeMeshes.values()) {
            mesh.release();
        }
        activeMeshes.clear();
    }
}