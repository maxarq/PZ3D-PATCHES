package com.max.pz3dfix;

import java.util.*;

public final class FarMeshBuilder {

    public static final float FLOOR_HEIGHT = 2.44949f;
    public static final float LOW_WALL_HEIGHT = 1.15f;
    public static final float WALL_OFFSET = 0.079f;

    public static final int FLOATS_PER_VERTEX = 13;
    public static final int VERTICES_PER_QUAD = 4;
    public static final int INDICES_PER_QUAD = 6;

    public static final class Batch {
        public final int textureId;
        public final int firstIndex;
        public int indexCount;
        public final boolean isOverlay;

        public Batch(int textureId, int firstIndex, int indexCount, boolean isOverlay) {
            this.textureId = textureId;
            this.firstIndex = firstIndex;
            this.indexCount = indexCount;
            this.isOverlay = isOverlay;
        }
    }

    public static final class BuildResult {
        public final float[] terrainVertexData;
        public final int terrainVertexCount;
        public final List<Batch> terrainBatches;

        public final float[] treeVertexData;
        public final int treeVertexCount;
        public final List<Batch> treeBatches;

        public BuildResult(float[] terrainVertexData, int terrainVertexCount, List<Batch> terrainBatches,
                           float[] treeVertexData, int treeVertexCount, List<Batch> treeBatches) {
            this.terrainVertexData = terrainVertexData;
            this.terrainVertexCount = terrainVertexCount;
            this.terrainBatches = terrainBatches;
            this.treeVertexData = treeVertexData;
            this.treeVertexCount = treeVertexCount;
            this.treeBatches = treeBatches;
        }
    }

    public static BuildResult buildBlock(FarBlockData block, FarTileClassifier.CellDictionary dict) {
        if (dict == null || dict.size == 0) {
            return new BuildResult(new float[0], 0, Collections.emptyList(), new float[0], 0, Collections.emptyList());
        }

        Map<Integer, Integer> opaqueCounts = new HashMap<>(16);
        Map<Integer, Integer> overlayCounts = new HashMap<>(8);

        // 1. Base Ground
        for (int i = 0; i < FarBlockData.TOTAL_SQUARES; i++) {
            int tileId = block.baseGroundTile[i];
            if (tileId >= 0 && tileId < dict.size) {
                int texId = dict.textureId[tileId];
                if (texId > 0) opaqueCounts.merge(texId, 1, Integer::sum);
            }
        }

        // 2. Walls
        for (int i = 0; i < block.wallCount; i++) {
            long rec = block.walls[i];
            int tileId = FarBlockData.unpackTileId(rec);
            byte type = FarBlockData.unpackType(rec);
            if (tileId >= 0 && tileId < dict.size) {
                int texId = dict.textureId[tileId];
                if (texId > 0) {
                    int quads = (type == FarTileType.WALL_NW_CORNER || type == FarTileType.WALL_LOW_NW_CORNER) ? 4 : 2;
                    opaqueCounts.merge(texId, quads, Integer::sum);
                }
            }
        }

        // 3. Flat Roofs
        for (int i = 0; i < FarBlockData.TOTAL_SQUARES; i++) {
            int tileId = block.roofTile[i];
            if (tileId >= 0 && tileId < dict.size) {
                int texId = dict.textureId[tileId];
                if (texId > 0) opaqueCounts.merge(texId, 1, Integer::sum);
            }
        }

        // 4. Overlays
        for (int i = 0; i < block.overlayCount; i++) {
            int tileId = FarBlockData.unpackTileId(block.overlays[i]);
            if (tileId >= 0 && tileId < dict.size) {
                int texId = dict.textureId[tileId];
                if (texId > 0) overlayCounts.merge(texId, 1, Integer::sum);
            }
        }

        // 5. Billboards (Trees, Foliage, Posts, Street Fixtures)
        Map<Integer, Integer> treeTextureCounts = new HashMap<>(8);
        for (int i = 0; i < block.treeCount; i++) {
            int tileId = FarBlockData.unpackTileId(block.trees[i]);
            if (tileId >= 0 && tileId < dict.size) {
                int texId = dict.textureId[tileId];
                if (texId > 0) treeTextureCounts.merge(texId, 1, Integer::sum);
            }
        }

        int totalTerrainQuads = 0;
        for (int c : opaqueCounts.values()) totalTerrainQuads += c;
        for (int c : overlayCounts.values()) totalTerrainQuads += c;

        int totalTerrainVerts = totalTerrainQuads * VERTICES_PER_QUAD;
        float[] terrainVertexData = new float[totalTerrainVerts * FLOATS_PER_VERTEX];
        List<Batch> terrainBatches = new ArrayList<>(opaqueCounts.size() + overlayCounts.size());

        Map<Integer, Integer> terrainOffsets = new HashMap<>(opaqueCounts.size() + overlayCounts.size());
        int currIndexOffset = 0;
        int currVertexOffset = 0;

        for (Map.Entry<Integer, Integer> entry : opaqueCounts.entrySet()) {
            int texId = entry.getKey();
            int quadCount = entry.getValue();
            terrainOffsets.put(texId, currVertexOffset);
            terrainBatches.add(new Batch(texId, currIndexOffset, quadCount * INDICES_PER_QUAD, false));
            currVertexOffset += quadCount * VERTICES_PER_QUAD * FLOATS_PER_VERTEX;
            currIndexOffset += quadCount * INDICES_PER_QUAD;
        }

        for (Map.Entry<Integer, Integer> entry : overlayCounts.entrySet()) {
            int texId = entry.getKey();
            int quadCount = entry.getValue();
            terrainOffsets.put(-texId, currVertexOffset);
            terrainBatches.add(new Batch(texId, currIndexOffset, quadCount * INDICES_PER_QUAD, true));
            currVertexOffset += quadCount * VERTICES_PER_QUAD * FLOATS_PER_VERTEX;
            currIndexOffset += quadCount * INDICES_PER_QUAD;
        }

        // Emit Solid Ground
        for (int y = 0; y < FarBlockData.BLOCK_SIZE; y++) {
            for (int x = 0; x < FarBlockData.BLOCK_SIZE; x++) {
                int tileId = block.baseGroundTile[x + (y * FarBlockData.BLOCK_SIZE)];
                if (tileId < 0 || tileId >= dict.size) continue;
                int texId = dict.textureId[tileId];
                if (texId <= 0) continue;

                int off = terrainOffsets.get(texId);
                terrainOffsets.put(texId, off + (VERTICES_PER_QUAD * FLOATS_PER_VERTEX));

                float wx0 = block.worldMinX + x;
                float wy0 = block.worldMinY + y;
                float wx1 = wx0 + 1.0f;
                float wy1 = wy0 + 1.0f;

                int bOff = tileId * 4;
                float cx0 = dict.uvBounds[bOff];
                float cy0 = dict.uvBounds[bOff + 1];
                float cx1 = dict.uvBounds[bOff + 2];
                float cy1 = dict.uvBounds[bOff + 3];

                int cOff = tileId * 8;
                putVertex(terrainVertexData, off,      wx0, wy0, 0f, 0f, 0f, 1f, dict.uvCornersN[cOff],     dict.uvCornersN[cOff + 1], cx0, cy0, cx1, cy1, 0f);
                putVertex(terrainVertexData, off + 13, wx1, wy0, 0f, 0f, 0f, 1f, dict.uvCornersN[cOff + 2], dict.uvCornersN[cOff + 3], cx0, cy0, cx1, cy1, 0f);
                putVertex(terrainVertexData, off + 26, wx1, wy1, 0f, 0f, 0f, 1f, dict.uvCornersN[cOff + 4], dict.uvCornersN[cOff + 5], cx0, cy0, cx1, cy1, 0f);
                putVertex(terrainVertexData, off + 39, wx0, wy1, 0f, 0f, 0f, 1f, dict.uvCornersN[cOff + 6], dict.uvCornersN[cOff + 7], cx0, cy0, cx1, cy1, 0f);
            }
        }

        // Emit Walls
        for (int i = 0; i < block.wallCount; i++) {
            long rec = block.walls[i];
            int tileId = FarBlockData.unpackTileId(rec);
            int texId = dict.textureId[tileId];
            if (texId <= 0) continue;

            int lx = FarBlockData.unpackLocalX(rec);
            int ly = FarBlockData.unpackLocalY(rec);
            int z = FarBlockData.unpackZ(rec);
            byte type = FarBlockData.unpackType(rec);

            float wx = block.worldMinX + lx;
            float wy = block.worldMinY + ly;
            float wz0 = z * FLOOR_HEIGHT;
            float height = FarTileType.isLowWall(type) ? LOW_WALL_HEIGHT : FLOOR_HEIGHT;
            float wz1 = wz0 + height;

            int bOff = tileId * 4;
            float cx0 = dict.uvBounds[bOff];
            float cy0 = dict.uvBounds[bOff + 1];
            float cx1 = dict.uvBounds[bOff + 2];
            float cy1 = dict.uvBounds[bOff + 3];

            int cOff = tileId * 8;

            if (type == FarTileType.EXTERIOR_WALL_N || type == FarTileType.EXTERIOR_WALL_LOW_N ||
                type == FarTileType.WALL_NW_CORNER || type == FarTileType.WALL_LOW_NW_CORNER) {
                float u0 = dict.uvCornersN[cOff];     float v0 = dict.uvCornersN[cOff + 1];
                float u1 = dict.uvCornersN[cOff + 2]; float v1 = dict.uvCornersN[cOff + 3];
                float u2 = dict.uvCornersN[cOff + 4]; float v2 = dict.uvCornersN[cOff + 5];
                float u3 = dict.uvCornersN[cOff + 6]; float v3 = dict.uvCornersN[cOff + 7];

                int off = terrainOffsets.get(texId);
                terrainOffsets.put(texId, off + (VERTICES_PER_QUAD * 2 * FLOATS_PER_VERTEX));

                float wyFront = wy + WALL_OFFSET;
                putVertex(terrainVertexData, off,      wx,     wyFront, wz0, 0f, -1f, 0f, u0, v0, cx0, cy0, cx1, cy1, 1f);
                putVertex(terrainVertexData, off + 13, wx + 1, wyFront, wz0, 0f, -1f, 0f, u1, v1, cx0, cy0, cx1, cy1, 1f);
                putVertex(terrainVertexData, off + 26, wx + 1, wyFront, wz1, 0f, -1f, 0f, u2, v2, cx0, cy0, cx1, cy1, 1f);
                putVertex(terrainVertexData, off + 39, wx,     wyFront, wz1, 0f, -1f, 0f, u3, v3, cx0, cy0, cx1, cy1, 1f);

                float wyBack = wy - WALL_OFFSET;
                putVertex(terrainVertexData, off + 52, wx + 1, wyBack, wz0, 0f, 1f, 0f, u1, v1, cx0, cy0, cx1, cy1, 1f);
                putVertex(terrainVertexData, off + 65, wx,     wyBack, wz0, 0f, 1f, 0f, u0, v0, cx0, cy0, cx1, cy1, 1f);
                putVertex(terrainVertexData, off + 78, wx,     wyBack, wz1, 0f, 1f, 0f, u3, v3, cx0, cy0, cx1, cy1, 1f);
                putVertex(terrainVertexData, off + 91, wx + 1, wyBack, wz1, 0f, 1f, 0f, u2, v2, cx0, cy0, cx1, cy1, 1f);
            }

            if (type == FarTileType.EXTERIOR_WALL_W || type == FarTileType.EXTERIOR_WALL_LOW_W ||
                type == FarTileType.WALL_NW_CORNER || type == FarTileType.WALL_LOW_NW_CORNER) {
                float u0 = dict.uvCornersW[cOff];     float v0 = dict.uvCornersW[cOff + 1];
                float u1 = dict.uvCornersW[cOff + 2]; float v1 = dict.uvCornersW[cOff + 3];
                float u2 = dict.uvCornersW[cOff + 4]; float v2 = dict.uvCornersW[cOff + 5];
                float u3 = dict.uvCornersW[cOff + 6]; float v3 = dict.uvCornersW[cOff + 7];

                int off = terrainOffsets.get(texId);
                terrainOffsets.put(texId, off + (VERTICES_PER_QUAD * 2 * FLOATS_PER_VERTEX));

                float wxFront = wx + WALL_OFFSET;
                putVertex(terrainVertexData, off,      wxFront, wy,     wz0, -1f, 0f, 0f, u0, v0, cx0, cy0, cx1, cy1, 1f);
                putVertex(terrainVertexData, off + 13, wxFront, wy + 1, wz0, -1f, 0f, 0f, u1, v1, cx0, cy0, cx1, cy1, 1f);
                putVertex(terrainVertexData, off + 26, wxFront, wy + 1, wz1, -1f, 0f, 0f, u2, v2, cx0, cy0, cx1, cy1, 1f);
                putVertex(terrainVertexData, off + 39, wxFront, wy,     wz1, -1f, 0f, 0f, u3, v3, cx0, cy0, cx1, cy1, 1f);

                float wxBack = wx - WALL_OFFSET;
                putVertex(terrainVertexData, off + 52, wxBack, wy + 1, wz0, 1f, 0f, 0f, u1, v1, cx0, cy0, cx1, cy1, 1f);
                putVertex(terrainVertexData, off + 65, wxBack, wy,     wz0, 1f, 0f, 0f, u0, v0, cx0, cy0, cx1, cy1, 1f);
                putVertex(terrainVertexData, off + 78, wxBack, wy,     wz1, 1f, 0f, 0f, u3, v3, cx0, cy0, cx1, cy1, 1f);
                putVertex(terrainVertexData, off + 91, wxBack, wy + 1, wz1, 1f, 0f, 0f, u2, v2, cx0, cy0, cx1, cy1, 1f);
            }
        }

        // Emit Flat Roofs
        for (int y = 0; y < FarBlockData.BLOCK_SIZE; y++) {
            for (int x = 0; x < FarBlockData.BLOCK_SIZE; x++) {
                int sqIndex = x + (y * FarBlockData.BLOCK_SIZE);
                int tileId = block.roofTile[sqIndex];
                if (tileId < 0 || tileId >= dict.size) continue;
                int texId = dict.textureId[tileId];
                if (texId <= 0) continue;

                int off = terrainOffsets.get(texId);
                terrainOffsets.put(texId, off + (VERTICES_PER_QUAD * FLOATS_PER_VERTEX));

                int z = block.roofZ[sqIndex];
                float wx0 = block.worldMinX + x;
                float wy0 = block.worldMinY + y;
                float wx1 = wx0 + 1.0f;
                float wy1 = wy0 + 1.0f;
                float wz = z * FLOOR_HEIGHT;

                int bOff = tileId * 4;
                float u0 = dict.uvBounds[bOff];
                float v0 = dict.uvBounds[bOff + 1];
                float u1 = dict.uvBounds[bOff + 2];
                float v1 = dict.uvBounds[bOff + 3];

                float uMid = (u0 + u1) * 0.5f;
                float vMid = (v0 + v1) * 0.5f;
                float halfW = (u1 - u0) * 0.22f;
                float halfH = (v1 - v0) * 0.22f;

                float ru0 = uMid - halfW;
                float ru1 = uMid + halfW;
                float rv0 = vMid - halfH;
                float rv1 = vMid + halfH;

                putVertex(terrainVertexData, off,      wx0, wy0, wz, 0f, 0f, 1f, ru0, rv1, ru0, rv0, ru1, rv1, 2f);
                putVertex(terrainVertexData, off + 13, wx1, wy0, wz, 0f, 0f, 1f, ru1, rv1, ru0, rv0, ru1, rv1, 2f);
                putVertex(terrainVertexData, off + 26, wx1, wy1, wz, 0f, 0f, 1f, ru1, rv0, ru0, rv0, ru1, rv1, 2f);
                putVertex(terrainVertexData, off + 39, wx0, wy1, wz, 0f, 0f, 1f, ru0, rv0, ru0, rv0, ru1, rv1, 2f);
            }
        }

        // Emit Overlays
        for (int i = 0; i < block.overlayCount; i++) {
            long rec = block.overlays[i];
            int tileId = FarBlockData.unpackTileId(rec);
            int texId = dict.textureId[tileId];
            if (texId <= 0) continue;

            int off = terrainOffsets.get(-texId);
            terrainOffsets.put(-texId, off + (VERTICES_PER_QUAD * FLOATS_PER_VERTEX));

            int lx = FarBlockData.unpackLocalX(rec);
            int ly = FarBlockData.unpackLocalY(rec);
            int layer = FarBlockData.unpackLayer(rec);

            float wx0 = block.worldMinX + lx;
            float wy0 = block.worldMinY + ly;
            float wx1 = wx0 + 1.0f;
            float wy1 = wy0 + 1.0f;

            float oz = 0.002f + (layer * 0.004f);

            int bOff = tileId * 4;
            float cx0 = dict.uvBounds[bOff];
            float cy0 = dict.uvBounds[bOff + 1];
            float cx1 = dict.uvBounds[bOff + 2];
            float cy1 = dict.uvBounds[bOff + 3];

            int cOff = tileId * 8;
            putVertex(terrainVertexData, off,      wx0, wy0, oz, 0f, 0f, 1f, dict.uvCornersN[cOff],     dict.uvCornersN[cOff + 1], cx0, cy0, cx1, cy1, 0f);
            putVertex(terrainVertexData, off + 13, wx1, wy0, oz, 0f, 0f, 1f, dict.uvCornersN[cOff + 2], dict.uvCornersN[cOff + 3], cx0, cy0, cx1, cy1, 0f);
            putVertex(terrainVertexData, off + 26, wx1, wy1, oz, 0f, 0f, 1f, dict.uvCornersN[cOff + 4], dict.uvCornersN[cOff + 5], cx0, cy0, cx1, cy1, 0f);
            putVertex(terrainVertexData, off + 39, wx0, wy1, oz, 0f, 0f, 1f, dict.uvCornersN[cOff + 6], dict.uvCornersN[cOff + 7], cx0, cy0, cx1, cy1, 0f);
        }

        // Emit All Billboards (Trees, Foliage, Posts, Street Fixtures)
        int totalTreeQuads = 0;
        for (int c : treeTextureCounts.values()) totalTreeQuads += c;

        int totalTreeVerts = totalTreeQuads * VERTICES_PER_QUAD;
        float[] treeVertexData = new float[totalTreeVerts * FLOATS_PER_VERTEX];
        List<Batch> treeBatches = new ArrayList<>(treeTextureCounts.size());

        Map<Integer, Integer> treeOffsets = new HashMap<>(treeTextureCounts.size());
        currIndexOffset = 0;
        currVertexOffset = 0;

        for (Map.Entry<Integer, Integer> entry : treeTextureCounts.entrySet()) {
            int texId = entry.getKey();
            int quadCount = entry.getValue();
            treeOffsets.put(texId, currVertexOffset);
            treeBatches.add(new Batch(texId, currIndexOffset, quadCount * INDICES_PER_QUAD, false));
            currVertexOffset += quadCount * VERTICES_PER_QUAD * FLOATS_PER_VERTEX;
            currIndexOffset += quadCount * INDICES_PER_QUAD;
        }

        for (int i = 0; i < block.treeCount; i++) {
            long rec = block.trees[i];
            int tileId = FarBlockData.unpackTileId(rec);
            int texId = dict.textureId[tileId];
            if (texId <= 0) continue;

            int off = treeOffsets.get(texId);
            treeOffsets.put(texId, off + (VERTICES_PER_QUAD * FLOATS_PER_VERTEX));

            int lx = FarBlockData.unpackLocalX(rec);
            int ly = FarBlockData.unpackLocalY(rec);
            int z = FarBlockData.unpackZ(rec);

            float rx = block.worldMinX + lx + 0.5f;
            float ry = block.worldMinY + ly + 0.5f;
            float rz = z * FLOOR_HEIGHT;

            int sizeOff = tileId * 2;
            float tw = (dict.billboardSize != null) ? dict.billboardSize[sizeOff] : 0f;
            float th = (dict.billboardSize != null) ? dict.billboardSize[sizeOff + 1] : 0f;
            if (tw <= 0.0f) tw = 1.0f;
            if (th <= 0.0f) th = 1.5f;

            int bOff = tileId * 4;
            float u0 = dict.uvBounds[bOff];
            float v0 = dict.uvBounds[bOff + 1];
            float u1 = dict.uvBounds[bOff + 2];
            float v1 = dict.uvBounds[bOff + 3];

            putTreeVertex(treeVertexData, off,      rx, ry, rz, -0.5f, 0.0f, tw, th, u0, v1, u0, v0, u1, v1);
            putTreeVertex(treeVertexData, off + 13, rx, ry, rz,  0.5f, 0.0f, tw, th, u1, v1, u0, v0, u1, v1);
            putTreeVertex(treeVertexData, off + 26, rx, ry, rz,  0.5f, 1.0f, tw, th, u1, v0, u0, v0, u1, v1);
            putTreeVertex(treeVertexData, off + 39, rx, ry, rz, -0.5f, 1.0f, tw, th, u0, v0, u0, v0, u1, v1);
        }

        return new BuildResult(terrainVertexData, totalTerrainVerts, terrainBatches,
                treeVertexData, totalTreeVerts, treeBatches);
    }

    private static void putVertex(float[] data, int off,
                                  float x, float y, float z,
                                  float nx, float ny, float nz,
                                  float u, float v,
                                  float cx0, float cy0, float cx1, float cy1,
                                  float kind) {
        data[off]      = x;
        data[off + 1]  = y;
        data[off + 2]  = z;
        data[off + 3]  = nx;
        data[off + 4]  = ny;
        data[off + 5]  = nz;
        data[off + 6]  = u;
        data[off + 7]  = v;
        data[off + 8]  = cx0;
        data[off + 9]  = cy0;
        data[off + 10] = cx1;
        data[off + 11] = cy1;
        data[off + 12] = kind;
    }

    private static void putTreeVertex(float[] data, int off,
                                      float rx, float ry, float rz,
                                      float cornerX, float cornerY,
                                      float w, float h,
                                      float u, float v,
                                      float cx0, float cy0, float cx1, float cy1) {
        data[off]      = rx;
        data[off + 1]  = ry;
        data[off + 2]  = rz;
        data[off + 3]  = cornerX;
        data[off + 4]  = cornerY;
        data[off + 5]  = w;
        data[off + 6]  = h;
        data[off + 7]  = u;
        data[off + 8]  = v;
        data[off + 9]  = cx0;
        data[off + 10] = cy0;
        data[off + 11] = cx1;
        data[off + 12] = cy1;
    }
}