package com.max.pz3dfix;

import org.joml.FrustumIntersection;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

public final class FarBlockMesh {

    public static final int MAX_SHARED_QUADS = 65536;
    private static int sharedIbo = 0;

    public final float minX;
    public final float minY;
    public final float minZ;
    public final float maxX;
    public final float maxY;
    public final float maxZ;

    private int terrainVao = 0;
    private int terrainVbo = 0;
    private List<FarMeshBuilder.Batch> terrainBatches = null;

    private int treeVao = 0;
    private int treeVbo = 0;
    private List<FarMeshBuilder.Batch> treeBatches = null;

    private boolean uploaded = false;

    public FarBlockMesh(float worldMinX, float worldMinY) {
        this(worldMinX, worldMinY, 24.0f);
    }

    public FarBlockMesh(float worldMinX, float worldMinY, float blockMaxZ) {
        this.minX = worldMinX;
        this.minY = worldMinY;
        this.minZ = 0.0f;
        this.maxX = worldMinX + FarBlockData.BLOCK_SIZE;
        this.maxY = worldMinY + FarBlockData.BLOCK_SIZE;
        this.maxZ = Math.max(24.0f, blockMaxZ + 4.0f);
    }

    public static void initSharedIndexBuffer() {
        if (sharedIbo != 0) return;

        IntBuffer ib = BufferUtils.createIntBuffer(MAX_SHARED_QUADS * FarMeshBuilder.INDICES_PER_QUAD);
        for (int i = 0; i < MAX_SHARED_QUADS; i++) {
            int base = i * 4;
            ib.put(base);
            ib.put(base + 1);
            ib.put(base + 2);
            ib.put(base);
            ib.put(base + 2);
            ib.put(base + 3);
        }
        ib.flip();

        sharedIbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, sharedIbo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public void upload(FarMeshBuilder.BuildResult result) {
        initSharedIndexBuffer();

        int stride = FarMeshBuilder.FLOATS_PER_VERTEX * 4;

        if (result.terrainVertexCount > 0) {
            this.terrainBatches = result.terrainBatches;

            FloatBuffer vb = BufferUtils.createFloatBuffer(result.terrainVertexData.length);
            vb.put(result.terrainVertexData).flip();

            terrainVao = GL30.glGenVertexArrays();
            terrainVbo = GL15.glGenBuffers();

            GL30.glBindVertexArray(terrainVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, terrainVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vb, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, sharedIbo);

            for (int i = 0; i < 5; i++) GL20.glEnableVertexAttribArray(i);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 12L);
            GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 24L);
            GL20.glVertexAttribPointer(3, 4, GL11.GL_FLOAT, false, stride, 32L);
            GL20.glVertexAttribPointer(4, 1, GL11.GL_FLOAT, false, stride, 48L);

            GL30.glBindVertexArray(0);
        }

        if (result.treeVertexCount > 0) {
            this.treeBatches = result.treeBatches;

            FloatBuffer tvb = BufferUtils.createFloatBuffer(result.treeVertexData.length);
            tvb.put(result.treeVertexData).flip();

            treeVao = GL30.glGenVertexArrays();
            treeVbo = GL15.glGenBuffers();

            GL30.glBindVertexArray(treeVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, treeVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, tvb, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, sharedIbo);

            for (int i = 0; i < 5; i++) GL20.glEnableVertexAttribArray(i);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 12L);
            GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 24L);
            GL20.glVertexAttribPointer(3, 2, GL11.GL_FLOAT, false, stride, 32L);
            GL20.glVertexAttribPointer(4, 4, GL11.GL_FLOAT, false, stride, 40L);

            GL30.glBindVertexArray(0);
        }

        this.uploaded = true;
    }

    public boolean isVisible(FrustumIntersection frustum) {
        return frustum == null || frustum.testAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public void drawTerrain() {
        if (!uploaded || terrainVao == 0 || terrainBatches == null || terrainBatches.isEmpty()) return;

        GL30.glBindVertexArray(terrainVao);

        // 1. Draw Opaque (Solid ground, walls, roofs)
        for (FarMeshBuilder.Batch b : terrainBatches) {
            if (!b.isOverlay && b.textureId > 0 && b.indexCount > 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, b.textureId);
                GL11.glDrawElements(GL11.GL_TRIANGLES, b.indexCount, GL11.GL_UNSIGNED_INT, (long) b.firstIndex * 4L);
            }
        }

        // 2. Draw Overlays (Blended road curves, curbs, dirt transitions)
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);

        for (FarMeshBuilder.Batch b : terrainBatches) {
            if (b.isOverlay && b.textureId > 0 && b.indexCount > 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, b.textureId);
                GL11.glDrawElements(GL11.GL_TRIANGLES, b.indexCount, GL11.GL_UNSIGNED_INT, (long) b.firstIndex * 4L);
            }
        }

        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);

        GL30.glBindVertexArray(0);
    }

    public void drawTrees() {
        if (!uploaded || treeVao == 0 || treeBatches == null || treeBatches.isEmpty()) return;

        GL30.glBindVertexArray(treeVao);
        for (FarMeshBuilder.Batch b : treeBatches) {
            if (b.textureId > 0 && b.indexCount > 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, b.textureId);
                GL11.glDrawElements(GL11.GL_TRIANGLES, b.indexCount, GL11.GL_UNSIGNED_INT, (long) b.firstIndex * 4L);
            }
        }
        GL30.glBindVertexArray(0);
    }

    public void release() {
        if (terrainVbo != 0) {
            GL15.glDeleteBuffers(terrainVbo);
            terrainVbo = 0;
        }
        if (terrainVao != 0) {
            GL30.glDeleteVertexArrays(terrainVao);
            terrainVao = 0;
        }
        if (treeVbo != 0) {
            GL15.glDeleteBuffers(treeVbo);
            treeVbo = 0;
        }
        if (treeVao != 0) {
            GL30.glDeleteVertexArrays(treeVao);
            treeVao = 0;
        }
        terrainBatches = null;
        treeBatches = null;
        uploaded = false;
    }
}