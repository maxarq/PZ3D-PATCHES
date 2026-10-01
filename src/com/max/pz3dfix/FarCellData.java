package com.max.pz3dfix;

import zombie.iso.LotHeader;

public final class FarCellData {

    public static final int CELL_SIZE = 256;
    public static final int BLOCKS_PER_AXIS = CELL_SIZE / FarBlockData.BLOCK_SIZE;

    public final int cellX;
    public final int cellY;
    public final int worldOriginX;
    public final int worldOriginY;

    public FarBlockData[][] blocks = new FarBlockData[BLOCKS_PER_AXIS][BLOCKS_PER_AXIS];
    public FarMeshBuilder.BuildResult[][] prebuiltMeshes = new FarMeshBuilder.BuildResult[BLOCKS_PER_AXIS][BLOCKS_PER_AXIS];

    public LotHeader lotHeader;
    public FarTileClassifier.CellDictionary dictionary;

    public volatile boolean ready = false;

    public FarCellData(int cellX, int cellY) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.worldOriginX = cellX * CELL_SIZE;
        this.worldOriginY = cellY * CELL_SIZE;

        for (int by = 0; by < BLOCKS_PER_AXIS; by++) {
            for (int bx = 0; bx < BLOCKS_PER_AXIS; bx++) {
                this.blocks[bx][by] = new FarBlockData(bx, by, this.worldOriginX, this.worldOriginY);
            }
        }
    }

    public void addTile(int cellTileX, int cellTileY, int z, byte type, int tileId) {
        if (blocks == null) return;
        int bx = Math.min(BLOCKS_PER_AXIS - 1, cellTileX / FarBlockData.BLOCK_SIZE);
        int by = Math.min(BLOCKS_PER_AXIS - 1, cellTileY / FarBlockData.BLOCK_SIZE);
        this.blocks[bx][by].addTile(cellTileX, cellTileY, z, type, tileId);
    }

    public void finish() {
        for (int by = 0; by < BLOCKS_PER_AXIS; by++) {
            for (int bx = 0; bx < BLOCKS_PER_AXIS; bx++) {
                FarBlockData b = this.blocks[bx][by];
                b.compact();
                this.prebuiltMeshes[bx][by] = FarMeshBuilder.buildBlock(b, this.dictionary);
                b.release(); // Discard raw long[] arrays immediately
            }
        }
        this.ready = true;
    }

    /**
     * Clears all CPU float arrays immediately after GPU VBO upload to free Java heap.
     */
    public void release() {
        this.blocks = null;
        this.prebuiltMeshes = null;
        this.dictionary = null;
        this.lotHeader = null;
    }
}