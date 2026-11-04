package name.vantaclient.client.util;

/** Pure planning math for the FarView quadtree. */
public final class FarTerrainLodMath {
    private static final double HORIZONTAL_VIEW_COS = Math.cos(Math.toRadians(68.0));
    private static final double HORIZONTAL_VIEW_SIN = Math.sin(Math.toRadians(68.0));

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

    /**
     * Conservative horizontal view-cone rejection for a tile's bounding circle.
     * This is algebraically equivalent to comparing atan2/cos angles, but avoids
     * per-tile trigonometric calls in the render loop. A small tolerance biases
     * borderline cases toward rendering rather than incorrectly culling them.
     */
    public static boolean isOutsideHorizontalViewCone(double dx, double dz,
                                                        double forwardX, double forwardZ,
                                                        double radius) {
        double distanceSquared = dx * dx + dz * dz;
        if (distanceSquared <= 1.0) return false;

        double boundedRadius = Math.max(0.0, radius);
        double distance = Math.sqrt(distanceSquared);
        double radiusSquared = boundedRadius * boundedRadius;
        double expandedDistance = Math.sqrt(distanceSquared + radiusSquared);
        double forwardProjection = dx * forwardX + dz * forwardZ;
        double left = forwardProjection * expandedDistance;
        double right = distanceSquared * HORIZONTAL_VIEW_COS
                - distance * boundedRadius * HORIZONTAL_VIEW_SIN;
        double tolerance = Math.max(1.0, distanceSquared + radiusSquared) * 1.0e-10;
        return left < right - tolerance;
    }

    /**
     * Precomputes whether each mesh cell is completely below sea level. Cell bits are
     * laid out row-major and allow the render loop to replace four height reads and
     * comparisons with one compact lookup.
     */
    public static long[] createWaterCellMask(int[] heights, int resolution,
                                              int seaLevel, boolean waterSurfaceEnabled) {
        if (resolution <= 0 || resolution > 64) {
            throw new IllegalArgumentException("Mesh resolution must be between 1 and 64.");
        }
        int side = resolution + 1;
        if (heights == null || heights.length != side * side) {
            throw new IllegalArgumentException("Height grid does not match mesh resolution.");
        }
        int cellCount = resolution * resolution;
        long[] mask = new long[(cellCount + Long.SIZE - 1) / Long.SIZE];
        if (!waterSurfaceEnabled) return mask;

        for (int z = 0; z < resolution; z++) {
            int row = z * side;
            int cellRow = z * resolution;
            for (int x = 0; x < resolution; x++) {
                int topLeft = row + x;
                int topRight = topLeft + 1;
                int bottomLeft = topLeft + side;
                int bottomRight = bottomLeft + 1;
                if (heights[topLeft] < seaLevel && heights[topRight] < seaLevel
                        && heights[bottomLeft] < seaLevel && heights[bottomRight] < seaLevel) {
                    int cellIndex = cellRow + x;
                    mask[cellIndex >>> 6] |= 1L << (cellIndex & 63);
                }
            }
        }
        return mask;
    }

    public static boolean isWaterCell(long[] mask, int cellIndex) {
        if (mask == null || cellIndex < 0) return false;
        int wordIndex = cellIndex >>> 6;
        return wordIndex < mask.length && (mask[wordIndex] & (1L << (cellIndex & 63))) != 0L;
    }

    /** Converts a mesh-grid sample index into the exact rounded world coordinate used for sampling. */
    public static int sampleWorldCoordinate(int origin, int span, int coordinate, int resolution) {
        if (resolution <= 0) return origin;
        long offset = (long) coordinate * span;
        return origin + (int) ((offset + resolution / 2L) / resolution);
    }

    /**
     * Adjusts FarView's draw budget with bounded steps to avoid frame-to-frame oscillation.
     * The caller controls adjustment cadence; this policy only responds to smoothed render cost.
     */
    /** Halves each RGB channel independently while keeping the opaque alpha byte intact. */
    public static int darkenOpaqueArgb(int color) {
        return 0xFF000000 | ((color & 0x00FEFEFE) >>> 1);
    }

    public static int adjustDrawBudget(int currentBudget, double averageRenderMs,
                                       int minimumBudget, int maximumBudget) {
        int minimum = Math.max(1, minimumBudget);
        int maximum = Math.max(minimum, maximumBudget);
        int bounded = Math.max(minimum, Math.min(maximum, currentBudget));
        if (!Double.isFinite(averageRenderMs)) return bounded;
        if (averageRenderMs > 4.0) {
            return Math.max(minimum, (int) Math.round(bounded * 0.85));
        }
        if (averageRenderMs < 2.0) {
            return Math.min(maximum, bounded + 2_000);
        }
        return bounded;
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
