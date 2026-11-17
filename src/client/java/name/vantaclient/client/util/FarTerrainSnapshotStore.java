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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Versioned, per-dimension disk cache for ingested FarView chunk surfaces.
 * All disk work runs on a bounded daemon executor; client/render threads never
 * block on filesystem access.
 */
final class FarTerrainSnapshotStore {
    private static final int MAGIC = 0x56464C44; // VFLD
    private static final int VERSION = 3;
    private static final int MAX_FILES_TO_LOAD = 16_384;
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

    void loadAll(Consumer<FarTerrainChunkSnapshot> consumer) {
        IO.execute(() -> {
            if (!Files.isDirectory(directory)) return;
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".vflod"))
                        .limit(MAX_FILES_TO_LOAD)
                        .forEach(path -> {
                            try {
                                FarTerrainChunkSnapshot snapshot = read(path);
                                if (snapshot != null) consumer.accept(snapshot);
                            } catch (IOException ignored) {
                                // A corrupt or interrupted cache entry is disposable.
                            }
                        });
            } catch (IOException ignored) {
                // The cache is optional; generation continues from live chunks/procedural terrain.
            }
        });
    }

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
