package com.max.pz3dfix;

public final class FarTileType {
    public static final byte DISCARD = 0;
    public static final byte GROUND_BASE = 1;
    public static final byte GROUND_OVERLAY = 2;
    public static final byte EXTERIOR_WALL_N = 3;
    public static final byte EXTERIOR_WALL_W = 4;
    public static final byte WALL_NW_CORNER = 5;
    public static final byte EXTERIOR_WALL_LOW_N = 6;
    public static final byte EXTERIOR_WALL_LOW_W = 7;
    public static final byte WALL_LOW_NW_CORNER = 8;
    public static final byte ROOF_FLAT = 9;
    public static final byte TREE_SMALL = 10;
    public static final byte TREE_MEDIUM = 11;
    public static final byte TREE_LARGE = 12;
    public static final byte FOLIAGE_BILLBOARD = 13;
    public static final byte FIXTURE_BILLBOARD = 14;

    private FarTileType() {}

    public static boolean isRoof(byte type) {
        return type == ROOF_FLAT;
    }

    public static boolean isWall(byte type) {
        return type >= EXTERIOR_WALL_N && type <= WALL_LOW_NW_CORNER;
    }

    public static boolean isLowWall(byte type) {
        return type >= EXTERIOR_WALL_LOW_N && type <= WALL_LOW_NW_CORNER;
    }

    public static boolean isTree(byte type) {
        return type >= TREE_SMALL && type <= TREE_LARGE;
    }

    public static boolean isBillboard(byte type) {
        return (type >= TREE_SMALL && type <= TREE_LARGE) || type == FOLIAGE_BILLBOARD || type == FIXTURE_BILLBOARD;
    }
}