package com.max.pz3dfix;

import org.joml.FrustumIntersection;

public final class FarCellMesh {

    public final int cellX;
    public final int cellY;
    public final FarBlockMesh[][] blockMeshes = new FarBlockMesh[FarCellData.BLOCKS_PER_AXIS][FarCellData.BLOCKS_PER_AXIS];

    public volatile boolean ready = false;

    // Approximate bounding sphere radius for a 32x32 block (16 * sqrt(2) ≈ 22.63f)
    private static final float BLOCK_BOUND_RADIUS = 23.0f;

    public FarCellMesh(int cellX, int cellY) {
        this.cellX = cellX;
        this.cellY = cellY;
    }

    public void build(FarCellData cellData) {
        for (int by = 0; by < FarCellData.BLOCKS_PER_AXIS; by++) {
            for (int bx = 0; bx < FarCellData.BLOCKS_PER_AXIS; bx++) {
                FarMeshBuilder.BuildResult res = cellData.prebuiltMeshes != null ? cellData.prebuiltMeshes[bx][by] : null;
                if (res != null) {
                    FarBlockMesh bMesh = new FarBlockMesh(
                            cellData.worldOriginX + (bx * FarBlockData.BLOCK_SIZE),
                            cellData.worldOriginY + (by * FarBlockData.BLOCK_SIZE),
                            cellData.blocks != null ? cellData.blocks[bx][by].maxZ : 24.0f
                    );
                    bMesh.upload(res);
                    this.blockMeshes[bx][by] = bMesh;
                }
            }
        }
        this.ready = true;
        cellData.release();
    }

    public void renderTerrain(FrustumIntersection frustum, float eyeX, float eyeY, float minNearDistance, float maxFarDistance) {
        if (!ready) return;

        for (int by = 0; by < FarCellData.BLOCKS_PER_AXIS; by++) {
            for (int bx = 0; bx < FarCellData.BLOCKS_PER_AXIS; bx++) {
                FarBlockMesh bm = this.blockMeshes[bx][by];
                if (bm == null) continue;

                float cx = (bm.minX + bm.maxX) * 0.5f;
                float cy = (bm.minY + bm.maxY) * 0.5f;
                float dx = cx - eyeX;
                float dy = cy - eyeY;
                float dist = (float) Math.sqrt((dx * dx) + (dy * dy));

                // If the entire block is closer than minNearDistance, skip on CPU
                if (dist + BLOCK_BOUND_RADIUS < minNearDistance) continue;
                // If the entire block is beyond maxFarDistance, skip on CPU
                if (dist - BLOCK_BOUND_RADIUS > maxFarDistance) continue;

                if (!bm.isVisible(frustum)) continue;

                bm.drawTerrain();
            }
        }
    }

    public void renderTrees(FrustumIntersection frustum, float eyeX, float eyeY, float minNearDistance, float maxFarDistance) {
        if (!ready) return;

        for (int by = 0; by < FarCellData.BLOCKS_PER_AXIS; by++) {
            for (int bx = 0; bx < FarCellData.BLOCKS_PER_AXIS; bx++) {
                FarBlockMesh bm = this.blockMeshes[bx][by];
                if (bm == null) continue;

                float cx = (bm.minX + bm.maxX) * 0.5f;
                float cy = (bm.minY + bm.maxY) * 0.5f;
                float dx = cx - eyeX;
                float dy = cy - eyeY;
                float dist = (float) Math.sqrt((dx * dx) + (dy * dy));

                if (dist + BLOCK_BOUND_RADIUS < minNearDistance) continue;
                if (dist - BLOCK_BOUND_RADIUS > maxFarDistance) continue;

                if (!bm.isVisible(frustum)) continue;

                bm.drawTrees();
            }
        }
    }

    public void release() {
        for (int by = 0; by < FarCellData.BLOCKS_PER_AXIS; by++) {
            for (int bx = 0; bx < FarCellData.BLOCKS_PER_AXIS; bx++) {
                if (this.blockMeshes[bx][by] != null) {
                    this.blockMeshes[bx][by].release();
                    this.blockMeshes[bx][by] = null;
                }
            }
        }
        this.ready = false;
    }
}