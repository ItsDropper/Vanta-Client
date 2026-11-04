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

    /** A queued or running job is obsolete when its world revision changed or its tile left the plan. */
    public static boolean isWorkObsolete(long taskRevision, long currentRevision, boolean tileDesired) {
        return taskRevision != currentRevision || !tileDesired;
    }

    /**
     * Adds a short pause between tile jobs when the client is under sustained load.
     * This trades far-terrain convergence speed for foreground frame/tick headroom.
     */
    public static int workerBackoffMillis(double worldRenderMs, double clientTickMs, int fps) {
        if (worldRenderMs <= 0.0 && clientTickMs <= 0.0) {
            if (fps > 0 && fps < 30) return 40;
            if (fps > 0 && fps < 45) return 20;
            return 0;
        }
        if (worldRenderMs >= 32.0 || clientTickMs >= 40.0 || (fps > 0 && fps < 30)) {
            return 40;
        }
        if (worldRenderMs >= 22.0 || clientTickMs >= 28.0 || (fps > 0 && fps < 45)) {
            return 20;
        }
        if (worldRenderMs >= 16.0 || clientTickMs >= 20.0 || (fps > 0 && fps < 60)) {
            return 8;
        }
        return 0;
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
