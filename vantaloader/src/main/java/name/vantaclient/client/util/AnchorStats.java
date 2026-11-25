package name.vantaclient.client.util;

public class AnchorStats {

    private static long totalScanTime = 0;
    private static long scanCount = 0;

    private static long lastScanTime = 0;
    private static long lastScanTimestamp = 0;

    public static void finishScan(long startTime) {
        lastScanTime = System.nanoTime() - startTime;
        lastScanTimestamp = System.currentTimeMillis();

        totalScanTime += lastScanTime;
        scanCount++;
    }

    public static double getLastScanTimeMs() {
        return lastScanTime / 1_000_000.0;
    }

    public static double getAverageScanTimeMs() {
        if (scanCount == 0) return 0;
        return (totalScanTime / (double) scanCount) / 1_000_000.0;
    }

    public static long getLastScanTimestamp() {
        return lastScanTimestamp;
    }
}