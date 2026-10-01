package com.max.pz3dfix;

import com.pavelvoronin.pz3d.WorldMirror;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public final class FarCoverageTexture {

    private static final FarCoverageTexture INSTANCE = new FarCoverageTexture();
    public static FarCoverageTexture getInstance() { return INSTANCE; }

    public static final int GRID_SIZE = 32;
    public static final int HALF_GRID = GRID_SIZE / 2; // 16 chunks

    private int textureId = 0;
    private final ByteBuffer buffer = BufferUtils.createByteBuffer(GRID_SIZE * GRID_SIZE);
    private final byte[] data = new byte[GRID_SIZE * GRID_SIZE];

    public int playerChunkX;
    public int playerChunkY;

    private static Field fVb;
    private static Method mKeyX;
    private static Method mKeyY;
    private static boolean reflectionOk = false;

    static {
        try {
            Class<?> sfClass = Class.forName("com.pavelvoronin.pz3d.StreamFade");
            fVb = sfClass.getDeclaredField("vb");
            fVb.setAccessible(true);

            Class<?> keyClass = Class.forName("com.pavelvoronin.pz3d.WorldStream$Key");
            mKeyX = keyClass.getDeclaredMethod("x");
            mKeyX.setAccessible(true);
            mKeyY = keyClass.getDeclaredMethod("y");
            mKeyY.setAccessible(true);

            reflectionOk = true;
        } catch (Throwable t) {
            System.err.println("[PZ3DFix] Failed to initialize reflection in FarCoverageTexture: " + t.getMessage());
        }
    }

    public void update(WorldMirror.Scene scene, float playerX, float playerY) {
        playerChunkX = Math.floorDiv((int) playerX, 8);
        playerChunkY = Math.floorDiv((int) playerY, 8);

        Arrays.fill(data, (byte) 0);

        if (reflectionOk && scene != null && fVb != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<float[], Set<Object>> vbMap = (Map<float[], Set<Object>>) fVb.get(null);
                if (vbMap != null) {
                    Set<Object> covered = vbMap.get(scene.vertices());
                    if (covered != null && !covered.isEmpty()) {
                        for (Object keyObj : covered) {
                            int cx = (int) mKeyX.invoke(keyObj);
                            int cy = (int) mKeyY.invoke(keyObj);

                            int dx = cx - playerChunkX;
                            int dy = cy - playerChunkY;

                            if (dx >= -HALF_GRID && dx < HALF_GRID && dy >= -HALF_GRID && dy < HALF_GRID) {
                                int u = dx + HALF_GRID;
                                int v = dy + HALF_GRID;
                                data[u + (v * GRID_SIZE)] = (byte) 255;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        buffer.clear();
        buffer.put(data);
        buffer.flip();

        if (textureId == 0) {
            textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, GRID_SIZE, GRID_SIZE, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, buffer);
        } else {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, GRID_SIZE, GRID_SIZE, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, buffer);
        }
    }

    public void bind(int textureUnit) {
        if (textureId == 0) return;
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }
}