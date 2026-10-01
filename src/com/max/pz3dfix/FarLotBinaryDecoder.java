package com.max.pz3dfix;

import zombie.ChunkMapFilenames;
import zombie.iso.IsoLot;
import zombie.iso.LotHeader;
import zombie.iso.MapFiles;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.concurrent.*;

public final class FarLotBinaryDecoder {

    private static final FarLotBinaryDecoder INSTANCE = new FarLotBinaryDecoder();
    public static FarLotBinaryDecoder getInstance() { return INSTANCE; }

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "PZ3D-LotpackDecoder");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 2);
        return t;
    });

    private final ConcurrentHashMap<Long, FarCellData> cellCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> activeRequests = new ConcurrentHashMap<>();

    public static long packCell(int cx, int cy) {
        return (((long) cx) << 32) | (cy & 0xFFFFFFFFL);
    }

    public FarCellData getCell(int cellX, int cellY) {
        return cellCache.get(packCell(cellX, cellY));
    }

    public void removeCell(int cellX, int cellY) {
        cellCache.remove(packCell(cellX, cellY));
    }

    public boolean isBusy() {
        return activeRequests.size() >= 3;
    }

    public void requestCell(int cellX, int cellY) {
        long key = packCell(cellX, cellY);
        if (cellCache.containsKey(key) || activeRequests.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }

        executor.submit(() -> {
            try {
                FarCellData cell = decodeCell(cellX, cellY);
                if (cell != null) {
                    cellCache.put(key, cell);
                }
            } catch (Throwable ignored) {
            } finally {
                activeRequests.remove(key);
            }
        });
    }

    public void pruneDistant(int centerCellX, int centerCellY, int maxCellRadius) {
        cellCache.entrySet().removeIf(entry -> {
            long k = entry.getKey();
            int cx = (int) (k >> 32);
            int cy = (int) k;
            boolean discard = Math.abs(cx - centerCellX) > maxCellRadius || Math.abs(cy - centerCellY) > maxCellRadius;
            if (discard && entry.getValue() != null) {
                entry.getValue().release();
            }
            return discard;
        });

        activeRequests.keySet().removeIf(k -> {
            int cx = (int) (k >> 32);
            int cy = (int) (long) k;
            return Math.abs(cx - centerCellX) > maxCellRadius || Math.abs(cy - centerCellY) > maxCellRadius;
        });
    }

    private FarCellData decodeCell(int cellX, int cellY) throws Exception {
        String filenamepack = "world_" + cellX + "_" + cellY + ".lotpack";
        String lotpackPath = findLotpackPath(filenamepack);
        if (lotpackPath == null) return null;

        File packFile = new File(lotpackPath);
        if (!packFile.exists()) return null;

        LotHeader header = findLotHeader(cellX, cellY);
        if (header == null) return null;

        FarCellData cellData = new FarCellData(cellX, cellY);
        cellData.lotHeader = header;

        FarTileClassifier.CellDictionary dict = FarTileClassifier.classify(header);
        cellData.dictionary = dict;

        try (RandomAccessFile raf = new RandomAccessFile(packFile, "r");
             FileChannel channel = raf.getChannel()) {

            long fileSize = channel.size();
            if (fileSize < 16) return null;

            ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            buf.order(ByteOrder.LITTLE_ENDIAN);

            boolean hasMagic = buf.get(0) == 'L' && buf.get(1) == 'O' && buf.get(2) == 'T' && buf.get(3) == 'P';
            int version = 0;
            int headerOffset = 4;
            if (hasMagic) {
                version = buf.getInt(4);
                headerOffset = (version >= 1) ? 12 : 4;
            }

            int minZ = Math.max(header.minLevel, -32);
            int maxZ = Math.min(header.maxLevel, 31);

            for (int lwx = 0; lwx < 32; lwx++) {
                for (int lwy = 0; lwy < 32; lwy++) {
                    int chunkIndex = (lwx * 32) + lwy;
                    int entryPos = headerOffset + (chunkIndex * 8);

                    if (entryPos + 4 > fileSize) continue;
                    int chunkPos = buf.getInt(entryPos);

                    if (chunkPos <= 0 || chunkPos >= fileSize) continue;

                    buf.position(chunkPos);
                    int skip = 0;

                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = 0; x < 8; x++) {
                            for (int y = 0; y < 8; y++) {
                                int cellTileX = (lwx * 8) + x;
                                int cellTileY = (lwy * 8) + y;

                                if (skip > 0) {
                                    skip--;
                                    continue;
                                }

                                if (buf.position() + 4 > fileSize) break;
                                int count = buf.getInt();

                                if (count == -1) {
                                    if (buf.position() + 4 > fileSize) break;
                                    skip = buf.getInt();
                                    if (skip > 0) skip--;
                                } else if (count > 0) {
                                    if (buf.position() + 4 > fileSize) break;
                                    buf.getInt();

                                    for (int n = 1; n < count; n++) {
                                        if (buf.position() + 4 > fileSize) break;
                                        int tileId = buf.getInt();

                                        if (tileId >= 0 && tileId < dict.size) {
                                            byte type = dict.type[tileId];
                                            if (type != FarTileType.DISCARD) {
                                                cellData.addTile(cellTileX, cellTileY, z, type, tileId);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        cellData.finish();
        return cellData;
    }

    private static String findLotpackPath(String packName) {
        String path = IsoLot.InfoFileNames.get(packName);
        if (path != null) return path;

        for (int i = 0; i < IsoLot.MapFiles.size(); i++) {
            MapFiles mf = IsoLot.MapFiles.get(i);
            if (mf != null && mf.infoFileNames != null) {
                path = mf.infoFileNames.get(packName);
                if (path != null) return path;
            }
        }
        return null;
    }

    private static LotHeader findLotHeader(int cellX, int cellY) {
        String headerName = ChunkMapFilenames.instance.getHeader(cellX, cellY);
        LotHeader header = IsoLot.InfoHeaders.get(headerName);
        if (header != null) return header;

        for (int i = 0; i < IsoLot.MapFiles.size(); i++) {
            MapFiles mf = IsoLot.MapFiles.get(i);
            if (mf != null && mf.infoHeaders != null) {
                header = mf.infoHeaders.get(headerName);
                if (header != null) return header;
            }
        }
        return null;
    }
}