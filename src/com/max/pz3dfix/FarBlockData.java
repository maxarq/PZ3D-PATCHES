package com.max.pz3dfix;

import java.util.Arrays;

public final class FarBlockData {

    public static final int BLOCK_SIZE = 32;
    public static final int TOTAL_SQUARES = BLOCK_SIZE * BLOCK_SIZE;

    public final int blockX;
    public final int blockY;
    public final float worldMinX;
    public final float worldMinY;
    public final float worldMaxX;
    public final float worldMaxY;

    public float maxZ = 0.0f;

    public final int[] baseGroundTile = new int[TOTAL_SQUARES];
    public final int[] roofTile = new int[TOTAL_SQUARES];
    public final byte[] roofZ = new byte[TOTAL_SQUARES];

    public final byte[] overlayLayerPerSquare = new byte[TOTAL_SQUARES];

    public long[] overlays = new long[16];
    public int overlayCount = 0;

    public long[] walls = new long[32];
    public int wallCount = 0;

    public long[] trees = new long[32];
    public int treeCount = 0;

    public FarBlockData(int blockX, int blockY, int cellWorldOriginX, int cellWorldOriginY) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.worldMinX = cellWorldOriginX + (blockX * BLOCK_SIZE);
        this.worldMinY = cellWorldOriginY + (blockY * BLOCK_SIZE);
        this.worldMaxX = this.worldMinX + BLOCK_SIZE;
        this.worldMaxY = this.worldMinY + BLOCK_SIZE;

        Arrays.fill(this.baseGroundTile, -1);
        Arrays.fill(this.roofTile, -1);
    }

    public static long pack(int localX, int localY, int z, byte type, int tileId, int layer) {
        long zOffset = (z + 32) & 0x3FL;
        return ((long) (localX & 0xFF))
                | (((long) (localY & 0xFF)) << 8)
                | (zOffset << 16)
                | (((long) (type & 0x3F)) << 22)
                | (((long) (tileId & 0xFFFFFL)) << 28)
                | (((long) (layer & 0xFF)) << 48);
    }

    public static int unpackLocalX(long packed) { return (int) (packed & 0xFFL); }
    public static int unpackLocalY(long packed) { return (int) ((packed >>> 8) & 0xFFL); }
    public static int unpackZ(long packed)      { return (int) (((packed >>> 16) & 0x3FL) - 32L); }
    public static byte unpackType(long packed)   { return (byte) ((packed >>> 22) & 0x3FL); }
    public static int unpackTileId(long packed) { return (int) ((packed >>> 28) & 0xFFFFFL); }
    public static int unpackLayer(long packed)  { return (int) ((packed >>> 48) & 0xFFL); }

    public void addTile(int cellTileX, int cellTileY, int z, byte type, int tileId) {
        int lx = cellTileX % BLOCK_SIZE;
        int ly = cellTileY % BLOCK_SIZE;
        int sqIndex = lx + (ly * BLOCK_SIZE);

        if (z == 0) {
            if (type == FarTileType.GROUND_BASE) {
                if (baseGroundTile[sqIndex] == -1) {
                    baseGroundTile[sqIndex] = tileId;
                }
                return;
            } else if (type == FarTileType.GROUND_OVERLAY) {
                int layer = ++overlayLayerPerSquare[sqIndex];
                if (overlayCount >= overlays.length) overlays = Arrays.copyOf(overlays, overlays.length * 2);
                overlays[overlayCount++] = pack(lx, ly, 0, type, tileId, layer);
                return;
            }
        }

        float tileZ = z * 2.44949f;
        if (tileZ > this.maxZ) this.maxZ = tileZ;

        if (FarTileType.isWall(type)) {
            long record = pack(lx, ly, z, type, tileId, 0);
            if (wallCount >= walls.length) walls = Arrays.copyOf(walls, walls.length * 2);
            walls[wallCount++] = record;
        } else if (FarTileType.isRoof(type) || (z > 0 && type == FarTileType.GROUND_BASE)) {
            if (roofTile[sqIndex] == -1) {
                roofTile[sqIndex] = tileId;
                roofZ[sqIndex] = (byte) z;
            } else if (z > (int) roofZ[sqIndex]) {
                roofTile[sqIndex] = tileId;
                roofZ[sqIndex] = (byte) z;
            } else if (z == (int) roofZ[sqIndex] && FarTileType.isRoof(type)) {
                roofTile[sqIndex] = tileId;
            }
        } else if (FarTileType.isBillboard(type)) {
            long record = pack(lx, ly, z, type, tileId, 0);
            if (treeCount >= trees.length) trees = Arrays.copyOf(trees, trees.length * 2);
            trees[treeCount++] = record;
        }
    }

    public void compact() {
        if (overlayCount < overlays.length) overlays = Arrays.copyOf(overlays, overlayCount);
        if (wallCount < walls.length) walls = Arrays.copyOf(walls, wallCount);
        if (treeCount < trees.length) trees = Arrays.copyOf(trees, treeCount);
    }

    public void release() {
        overlays = null;
        walls = null;
        trees = null;
        overlayCount = 0;
        wallCount = 0;
        treeCount = 0;
    }
}