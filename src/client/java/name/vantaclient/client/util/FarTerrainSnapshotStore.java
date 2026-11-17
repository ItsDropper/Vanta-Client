package name.vantaclient.client.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Versioned, per-dimension disk cache for surface samples and palette-compressed 3D chunk sections.
 * All disk work runs on a bounded daemon executor; client/render threads never
 * block on filesystem access.
 */
final class FarTerrainSnapshotStore {
    private static final int MAGIC = 0x56464C44; // VFLD
    private static final int VERSION = 4;
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(512),
            runnable -> {
                Thread thread = new Thread(runnable, "Vanta-FarTerrain-Storage");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    private final Path directory;

    FarTerrainSnapshotStore(Path worldRoot, String dimensionKey) {
        String safeDimension = dimensionKey.replace(':', '_').replace('/', '_').replace('\\', '_');
        this.directory = worldRoot.resolve("vanta").resolve("farview").resolve(safeDimension);
    }

    void loadNearest(int centerChunkX, int centerChunkZ, int maxSnapshots,
                     Consumer<FarTerrainChunkSnapshot> consumer) {
        if (maxSnapshots < 1) return;
        IO.execute(() -> {
            if (!Files.isDirectory(directory)) return;
            PriorityQueue<ChunkFile> nearest = new PriorityQueue<>(
                    Comparator.comparingLong(ChunkFile::distanceSquared).reversed());
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".vflod"))
                        .forEach(path -> {
                            ChunkFile candidate = parseChunkFile(path, centerChunkX, centerChunkZ);
                            if (candidate == null) return;
                            if (nearest.size() < maxSnapshots) {
                                nearest.add(candidate);
                            } else if (candidate.distanceSquared() < nearest.peek().distanceSquared()) {
                                nearest.poll();
                                nearest.add(candidate);
                            }
                        });
            } catch (IOException ignored) {
                return;
            }

            // Feed farther entries first so the bounded in-memory LRU retains
            // the closest explored chunks around the current player position.
            List<ChunkFile> ordered = new ArrayList<>(nearest);
            ordered.sort(Comparator.comparingLong(ChunkFile::distanceSquared).reversed());
            for (ChunkFile entry : ordered) {
                try {
                    FarTerrainChunkSnapshot snapshot = read(entry.path());
                    if (snapshot != null) consumer.accept(snapshot);
                } catch (IOException ignored) {
                    // A corrupt or interrupted cache entry is disposable.
                }
            }
        });
    }

    private static ChunkFile parseChunkFile(Path path, int centerChunkX, int centerChunkZ) {
        String name = path.getFileName().toString();
        if (!name.endsWith(".vflod")) return null;
        String coordinates = name.substring(0, name.length() - ".vflod".length());
        int separator = coordinates.lastIndexOf('_');
        if (separator <= 0 || separator == coordinates.length() - 1) return null;
        try {
            int chunkX = Integer.parseInt(coordinates.substring(0, separator));
            int chunkZ = Integer.parseInt(coordinates.substring(separator + 1));
            long dx = (long) chunkX - centerChunkX;
            long dz = (long) chunkZ - centerChunkZ;
            return new ChunkFile(path, dx * dx + dz * dz);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record ChunkFile(Path path, long distanceSquared) {}

    void save(FarTerrainChunkSnapshot snapshot) {
        IO.execute(() -> write(snapshot));
    }

    private FarTerrainChunkSnapshot read(Path file) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(file))))) {
            if (input.readInt() != MAGIC) return null;
            int version = input.readInt();
            if (version < 1 || version > VERSION) return null;
            return FarTerrainChunkSnapshot.read(input, version);
        }
    }

    private void write(FarTerrainChunkSnapshot snapshot) {
        Path temporary = directory.resolve(snapshot.chunkX() + "_" + snapshot.chunkZ() + ".tmp");
        Path destination = directory.resolve(snapshot.chunkX() + "_" + snapshot.chunkZ() + ".vflod");
        try {
            Files.createDirectories(directory);
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temporary))))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                snapshot.write(output);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignoredCleanup) {
                // Best-effort cleanup only.
            }
        }
    }
}
