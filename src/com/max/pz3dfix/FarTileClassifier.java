package com.max.pz3dfix;

import zombie.core.properties.PropertyContainer;
import zombie.core.textures.Texture;
import zombie.iso.IsoDirections;
import zombie.iso.LotHeader;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;

import java.util.ArrayList;

public final class FarTileClassifier {

    public static final class CellDictionary {
        public final byte[] type;
        public final int[] textureId;
        public final float[] uvBounds;
        public final float[] uvCornersN;
        public final float[] uvCornersW;
        public final float[] billboardSize; // [id * 2] = width, [id * 2 + 1] = height
        public final int size;

        public CellDictionary(int count) {
            this.size = count;
            this.type = new byte[count];
            this.textureId = new int[count];
            this.uvBounds = new float[count * 4];
            this.uvCornersN = new float[count * 8];
            this.uvCornersW = new float[count * 8];
            this.billboardSize = new float[count * 2];
        }
    }

    public static CellDictionary classify(LotHeader header) {
        if (header == null || header.tilesUsed == null) {
            return new CellDictionary(0);
        }

        ArrayList<String> tiles = header.tilesUsed;
        int count = tiles.size();
        CellDictionary dict = new CellDictionary(count);
        IsoSpriteManager sm = IsoSpriteManager.instance;

        for (int id = 0; id < count; id++) {
            String name = tiles.get(id);
            if (name == null || name.isEmpty()) {
                dict.type[id] = FarTileType.DISCARD;
                continue;
            }

            IsoSprite spr = (sm != null && sm.namedMap != null) ? sm.namedMap.get(name) : null;
            byte tileType = classifySprite(name, spr);
            dict.type[id] = tileType;

            if (tileType != FarTileType.DISCARD && spr != null) {
                try {
                    Texture tex = spr.getTextureForCurrentFrame(IsoDirections.N);
                    if (tex != null && tex.isReady()) {
                        dict.textureId[id] = tex.getTextureId() != null ? tex.getTextureId().getID() : 0;

                        int bOff = id * 4;
                        dict.uvBounds[bOff]     = tex.getXStart();
                        dict.uvBounds[bOff + 1] = tex.getYStart();
                        dict.uvBounds[bOff + 2] = tex.getXEnd();
                        dict.uvBounds[bOff + 3] = tex.getYEnd();

                        int cOff = id * 8;
                        boolean low = FarTileType.isLowWall(tileType);
                        computeWallUv(tex, true, dict.uvCornersN, cOff, low);
                        computeWallUv(tex, false, dict.uvCornersW, cOff, low);

                        if (tileType == FarTileType.GROUND_BASE || tileType == FarTileType.GROUND_OVERLAY) {
                            computeDiamondUv(tex, dict.uvCornersN, cOff, tileType == FarTileType.GROUND_OVERLAY);
                        } else if (FarTileType.isRoof(tileType) || FarTileType.isBillboard(tileType)) {
                            dict.uvCornersN[cOff]     = tex.getXStart(); dict.uvCornersN[cOff + 1] = tex.getYEnd();
                            dict.uvCornersN[cOff + 2] = tex.getXEnd();   dict.uvCornersN[cOff + 3] = tex.getYEnd();
                            dict.uvCornersN[cOff + 4] = tex.getXEnd();   dict.uvCornersN[cOff + 5] = tex.getYStart();
                            dict.uvCornersN[cOff + 6] = tex.getXStart(); dict.uvCornersN[cOff + 7] = tex.getYStart();
                        }

                        // Compute true metric billboard dimensions for all billboard types
                        if (FarTileType.isBillboard(tileType)) {
                            int sOff = id * 2;
                            if (FarTileType.isTree(tileType)) {
                                float tw = (tileType == FarTileType.TREE_LARGE) ? 7.5f : (tileType == FarTileType.TREE_MEDIUM) ? 4.5f : 2.5f;
                                float th = (tileType == FarTileType.TREE_LARGE) ? 13.5f : (tileType == FarTileType.TREE_MEDIUM) ? 7.0f : 3.8f;
                                dict.billboardSize[sOff]     = tw;
                                dict.billboardSize[sOff + 1] = th;
                            } else if (tileType == FarTileType.FOLIAGE_BILLBOARD) {
                                float scale = (tex.getWidthOrig() > 0) ? (tex.getWidthOrig() / 128.0f) : 1.0f;
                                float texH = tex.getHeight();
                                float texW = tex.getWidth();
                                float h = (texH * 2.44949f) / (192.0f * scale);
                                float w = h * (texW / Math.max(1.0f, texH));
                                dict.billboardSize[sOff]     = Math.max(0.7f, Math.min(2.0f, w));
                                dict.billboardSize[sOff + 1] = Math.max(0.6f, Math.min(2.2f, h));
                            } else if (tileType == FarTileType.FIXTURE_BILLBOARD) {
                                float scale = (tex.getWidthOrig() > 0) ? (tex.getWidthOrig() / 128.0f) : 1.0f;
                                float texH = tex.getHeight();
                                float texW = tex.getWidth();
                                float h = (texH * 2.44949f) / (192.0f * scale);
                                float w = h * (texW / Math.max(1.0f, texH));
                                dict.billboardSize[sOff]     = Math.max(0.35f, Math.min(2.5f, w));
                                dict.billboardSize[sOff + 1] = Math.max(0.7f, Math.min(7.5f, h));
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return dict;
    }

    private static void computeWallUv(Texture tex, boolean north, float[] out, int offset, boolean lowWall) {
        float widthOrig = tex.getWidthOrig();
        float heightOrig = tex.getHeightOrig();
        float offsetX = tex.getOffsetX();
        float offsetY = tex.getOffsetY();
        float width = tex.getWidth();
        float height = tex.getHeight();
        float xStart = tex.getXStart();
        float yStart = tex.getYStart();
        float xEnd = tex.getXEnd();
        float yEnd = tex.getYEnd();

        float wallH = lowWall ? 1.15f : 2.44949f;
        float[] uCoords = {0.0f, 1.0f, 1.0f, 0.0f};
        float[] hCoords = {0.0f, 0.0f, wallH, wallH};
        float scale = widthOrig / 128.0f;

        for (int i = 0; i < 4; i++) {
            float u = uCoords[i];
            float h = hCoords[i];

            float px = (widthOrig * 0.5f) + (((north ? 1.0f : -1.0f) * u * widthOrig) * 0.5f);
            float py = ((heightOrig - (widthOrig * 0.5f)) + ((u * widthOrig) * 0.25f)) - (((h / 2.44949f) * 192.0f) * scale);

            float atlasU = xStart + (((px - offsetX) / width) * (xEnd - xStart));
            float atlasV = yStart + (((py - offsetY) / height) * (yEnd - yStart));

            out[offset + (i * 2)]     = atlasU;
            out[offset + (i * 2) + 1] = atlasV;
        }
    }

    private static void computeDiamondUv(Texture tex, float[] out, int offset, boolean isOverlay) {
        float f = tex.getWidthOrig();
        float f2 = tex.getHeightOrig();
        float f3 = tex.getOffsetX();
        float f4 = tex.getOffsetY();
        float f5 = tex.getWidth();
        float f6 = tex.getHeight();
        float f7 = tex.getXStart();
        float f8 = tex.getYStart();
        float f9 = tex.getXEnd();
        float f10 = tex.getYEnd();

        float[] px = {f * 0.5f, f, f * 0.5f, 0.0f};
        float[] py = {f2 - (f * 0.5f), f2 - (f * 0.25f), f2, f2 - (f * 0.25f)};

        float insetScale = (isOverlay ? 6.0f : 3.0f) / f;
        float centerX = (px[0] + px[2]) * 0.5f;
        float centerY = (py[0] + py[2]) * 0.5f;

        for (int i = 0; i < 4; i++) {
            float dpx = px[i] + ((centerX - px[i]) * insetScale);
            float dpy = py[i] + ((centerY - py[i]) * insetScale);

            out[offset + (i * 2)]     = f7 + (((dpx - f3) / f5) * (f9 - f7));
            out[offset + (i * 2) + 1] = f8 + (((dpy - f4) / f6) * (f10 - f8));
        }
    }

    private static byte classifySprite(String name, IsoSprite spr) {
        PropertyContainer props = (spr != null) ? spr.getProperties() : null;
        IsoObjectType objType = (spr != null) ? spr.getTileType() : null;

        // 1. Trees
        if (objType == IsoObjectType.tree || isTreeName(name)) {
            if (name.contains("JUMBOXXL") || name.contains("JUMBOXL")) return FarTileType.TREE_LARGE;
            if (name.contains("JUMBO")) return FarTileType.TREE_MEDIUM;
            return FarTileType.TREE_SMALL;
        }

        // 2. Roofs (Only actual roof surface tiles)
        if (name.startsWith("roofs_") || (objType != null && isRoofObjType(objType))) {
            if (name.startsWith("roofs_accents_") || (props != null && props.has(IsoFlagType.isEave))) {
                return FarTileType.DISCARD;
            }
            return FarTileType.ROOF_FLAT;
        }

        // 3. Tall Grass, Weeds & Foliage
        if (isFoliageName(name)) {
            return FarTileType.FOLIAGE_BILLBOARD;
        }

        // 4. Street Fixtures, Posts, Poles & Signs
        if (isFixtureName(name, props)) {
            return FarTileType.FIXTURE_BILLBOARD;
        }

        // 5. Walls, Railings, Fences & Parapets
        boolean hasWallN = false;
        boolean hasWallW = false;
        boolean isLowWall = false;

        if (props != null) {
            hasWallN = props.has(IsoFlagType.WallN) ||
                    props.has(IsoFlagType.WallNTrans) ||
                    props.has(IsoFlagType.collideN) ||
                    props.has(IsoFlagType.cutN) ||
                    props.has(IsoFlagType.DoorWallN) ||
                    props.has(IsoFlagType.WindowN) ||
                    props.has(IsoFlagType.HoppableN);

            hasWallW = props.has(IsoFlagType.WallW) ||
                    props.has(IsoFlagType.WallWTrans) ||
                    props.has(IsoFlagType.collideW) ||
                    props.has(IsoFlagType.cutW) ||
                    props.has(IsoFlagType.DoorWallW) ||
                    props.has(IsoFlagType.WindowW) ||
                    props.has(IsoFlagType.HoppableW);

            if (props.has(IsoFlagType.HoppableN) || props.has(IsoFlagType.HoppableW) ||
                    props.has("FenceTypeLow") || props.has("Hoppable")) {
                isLowWall = true;
            }
        }

        if (objType != null) {
            if (objType == IsoObjectType.doorN || objType == IsoObjectType.windowFN || objType == IsoObjectType.doorFrN) hasWallN = true;
            if (objType == IsoObjectType.doorW || objType == IsoObjectType.windowFW || objType == IsoObjectType.doorFrW) hasWallW = true;
        }

        if (!hasWallN && !hasWallW && (name.startsWith("walls_") || name.startsWith("fencing_") || name.startsWith("fixtures_railings_") || name.startsWith("location_"))) {
            int suffix = parseSuffix(name);
            if (suffix >= 0) {
                if (suffix % 2 == 1) hasWallN = true;
                else hasWallW = true;
            }
            if (name.contains("_low_") || name.startsWith("fencing_") || name.startsWith("fixtures_railings_")) {
                isLowWall = true;
            }
        }

        if (hasWallN && hasWallW) return isLowWall ? FarTileType.WALL_LOW_NW_CORNER : FarTileType.WALL_NW_CORNER;
        if (hasWallN) return isLowWall ? FarTileType.EXTERIOR_WALL_LOW_N : FarTileType.EXTERIOR_WALL_N;
        if (hasWallW) return isLowWall ? FarTileType.EXTERIOR_WALL_LOW_W : FarTileType.EXTERIOR_WALL_W;

        // 6. Overlays (blends, curbs)
        if (name.startsWith("street_curbs_") ||
                name.startsWith("blends_grassoverlays_") ||
                (props != null && props.has(IsoFlagType.FloorOverlay))) {
            return FarTileType.GROUND_OVERLAY;
        }

        // 7. Solid Base Ground
        if (name.startsWith("blends_natural_01_") ||
                name.startsWith("blends_street_") ||
                name.startsWith("floors_exterior_") ||
                (props != null && (props.has(IsoFlagType.solidfloor) || props.has(IsoFlagType.water)))) {
            return FarTileType.GROUND_BASE;
        }

        return FarTileType.DISCARD;
    }

    private static boolean isTreeName(String name) {
        return name.startsWith("vegetation_trees_")
                || name.startsWith("jumbo_tree_")
                || name.startsWith("e_americanholly_")
                || name.startsWith("e_canadianhemlock_")
                || name.startsWith("e_virginiapine_")
                || name.startsWith("e_riverbirch_")
                || name.startsWith("e_cockspurhawthorn_")
                || name.startsWith("e_dogwood_")
                || name.startsWith("e_carolinasilverbell_")
                || name.startsWith("e_yellowwood_")
                || name.startsWith("e_easternredbud_")
                || name.startsWith("e_redmaple_")
                || name.startsWith("e_americanlinden_");
    }

    private static boolean isFoliageName(String name) {
        return name.startsWith("vegetation_foliage_")
                || name.startsWith("d_generic_")
                || name.startsWith("vegetation_ground_")
                || name.startsWith("f_bushes_")
                || name.startsWith("plants_");
    }

    private static boolean isFixtureName(String name, PropertyContainer props) {
        if (name.startsWith("street_decoration_")
                || name.startsWith("lighting_outdoor_")
                || name.startsWith("street_signs_")
                || name.startsWith("signs_")
                || name.startsWith("industry_railroad_")) {
            return true;
        }
        if (name.contains("_post") || name.contains("_pole") || name.contains("_bollard")
                || name.contains("_hydrant") || name.contains("_meter") || name.contains("_mailbox")
                || name.contains("_sign")) {
            return true;
        }
        if (props != null && (props.has("FenceTypePost") || props.has("Post") || props.has("FencePost"))) {
            return true;
        }
        return false;
    }

    private static boolean isRoofObjType(IsoObjectType type) {
        return type == IsoObjectType.WestRoofT || type == IsoObjectType.WestRoofM || type == IsoObjectType.WestRoofB;
    }

    private static int parseSuffix(String name) {
        int idx = name.lastIndexOf('_');
        if (idx < 0 || idx >= name.length() - 1) return -1;
        try {
            return Integer.parseInt(name.substring(idx + 1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}