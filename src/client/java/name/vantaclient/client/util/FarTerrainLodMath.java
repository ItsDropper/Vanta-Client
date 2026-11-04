package name.vantaclient.client.util;

/** Pure planning math for the FarView quadtree. */
public final class FarTerrainLodMath {
    private FarTerrainLodMath() {}

    /**
     * A tile plan is rebuilt only when it does not exist, the player has moved
     * four chunks on either axis, or a setting that affects tile topology changed.
     */
    public static boolean shouldRebuildPlan(boolean hasPlan,
                                            int lastPlayerChunkX,
                                            int lastPlayerChunkZ,
                                            int playerChunkX,
                                            int playerChunkZ,
                                            int lastSampleSpacing,
                                            int sampleSpacing,
                                            int lastDetailDistance,
                                            int detailDistance,
                                            int lastFarDistance,
                                            int farDistance) {
        if (!hasPlan) return true;
        if (Math.abs((long) playerChunkX - lastPlayerChunkX) >= 4L
                || Math.abs((long) playerChunkZ - lastPlayerChunkZ) >= 4L) {
            return true;
        }
        return sampleSpacing != lastSampleSpacing
                || detailDistance != lastDetailDistance
                || farDistance != lastFarDistance;
    }

    public static double distanceToRectangleSquared(double x, double z,
                                                     double minX, double minZ,
                                                     double maxX, double maxZ) {
        double dx = x < minX ? minX - x : x > maxX ? x - maxX : 0.0;
        double dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0.0;
        return dx * dx + dz * dz;
    }

    public static double farthestCornerDistanceSquared(double x, double z,
                                                        double minX, double minZ,
                                                        double maxX, double maxZ) {
        double dx = Math.max(Math.abs(x - minX), Math.abs(x - maxX));
        double dz = Math.max(Math.abs(z - minZ), Math.abs(z - maxZ));
        return dx * dx + dz * dz;
    }

    public static boolean isEntireTileInsideInnerRadius(double farthestDistanceSquared,
                                                          double innerRadiusSquared) {
        return farthestDistanceSquared <= innerRadiusSquared;
    }

    public static boolean isEntireTileInsideRenderBand(double nearestDistanceSquared,
                                                        double farthestDistanceSquared,
                                                        double innerRadiusSquared,
                                                        double outerRadiusSquared) {
        return nearestDistanceSquared >= innerRadiusSquared
                && farthestDistanceSquared <= outerRadiusSquared;
    }

    public static int desiredTileSizeForDistanceSquared(double distanceSquared) {
        if (distanceSquared < 64.0 * 64.0) return 8;
        if (distanceSquared < 128.0 * 128.0) return 16;
        if (distanceSquared < 256.0 * 256.0) return 32;
        if (distanceSquared < 512.0 * 512.0) return 64;
        if (distanceSquared < 1024.0 * 1024.0) return 128;
        if (distanceSquared < 1536.0 * 1536.0) return 256;
        return 512;
    }
}
