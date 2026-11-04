package name.vantaclient.client.integration.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded loopback-only TCP bridge between Minecraft's local socket and a remote
 * ordered byte stream (for example, a future WebRTC data-channel adapter).
 *
 * The bridge never binds to a LAN/public interface. Each local TCP connection gets
 * one independent remote stream. Copying is blocking and bounded to a fixed buffer,
 * providing natural write backpressure instead of accumulating unbounded queues.
 */
public final class LoopbackTcpBridge implements AutoCloseable {
    public static final int MAX_CONNECTION_LIMIT = 64;
    private static final int COPY_BUFFER_BYTES = 16 * 1024;

    /** A single bidirectional, ordered byte stream supplied by the transport layer. */
    public interface DuplexConnection extends AutoCloseable {
        InputStream inputStream();
        OutputStream outputStream();

        /**
         * Propagates EOF in this direction without prematurely closing the reverse
         * direction. A WebRTC adapter can encode this as a stream-close control frame.
         */
        void shutdownOutput() throws IOException;

        @Override
        void close() throws IOException;
    }

    @FunctionalInterface
    public interface RemoteConnector {
        /** Opens one fresh remote byte stream for one accepted local TCP client. */
        DuplexConnection connect() throws IOException;
    }

    private final ServerSocket listener;
    private final RemoteConnector connector;
    private final Semaphore connectionSlots;
    private final ExecutorService workers;
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread acceptThread;

    private LoopbackTcpBridge(ServerSocket listener, RemoteConnector connector,
                              int maximumConnections) {
        this.listener = listener;
        this.connector = connector;
        this.connectionSlots = new Semaphore(maximumConnections);
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task,
                    "Vanta tunnel worker " + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        // At most one connector task and two stream-copy tasks per active session.
        this.workers = Executors.newFixedThreadPool(maximumConnections * 3, factory);
        this.acceptThread = new Thread(this::acceptLoop, "Vanta loopback tunnel accept");
        this.acceptThread.setDaemon(true);
    }

    /**
     * Starts a listener on an ephemeral port bound strictly to the local loopback
     * interface. The returned port can be used as the guest's local Minecraft target.
     */
    public static LoopbackTcpBridge listen(RemoteConnector connector,
                                           int maximumConnections) throws IOException {
        if (connector == null) throw new IllegalArgumentException("Remote connector cannot be null.");
        if (maximumConnections < 1 || maximumConnections > MAX_CONNECTION_LIMIT) {
            throw new IllegalArgumentException("Maximum connections must be between 1 and "
                    + MAX_CONNECTION_LIMIT + ".");
        }

        ServerSocket listener = new ServerSocket();
        boolean bound = false;
        try {
            listener.setReuseAddress(false);
            listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0),
                    maximumConnections);
            LoopbackTcpBridge bridge = new LoopbackTcpBridge(
                    listener, connector, maximumConnections);
            bridge.acceptThread.start();
            bound = true;
            return bridge;
        } finally {
            if (!bound) listener.close();
        }
    }

    /** Wraps an already-connected socket as a byte-stream transport adapter. */
    public static DuplexConnection fromSocket(Socket socket) {
        if (socket == null) throw new IllegalArgumentException("Socket cannot be null.");
        return new DuplexConnection() {
            @Override
            public InputStream inputStream() {
                try {
                    return socket.getInputStream();
                } catch (IOException exception) {
                    throw new IllegalStateException("Socket input stream is unavailable.", exception);
                }
            }

            @Override
            public OutputStream outputStream() {
                try {
                    return socket.getOutputStream();
                } catch (IOException exception) {
                    throw new IllegalStateException("Socket output stream is unavailable.", exception);
                }
            }

            @Override
            public void shutdownOutput() throws IOException {
                socket.shutdownOutput();
            }

            @Override
            public void close() throws IOException {
                socket.close();
            }
        };
    }

    /** Local endpoint for the Minecraft client; always a loopback address. */
    public InetSocketAddress address() {
        return (InetSocketAddress) listener.getLocalSocketAddress();
    }

    public int port() {
        return listener.getLocalPort();
    }

    public int activeConnections() {
        return sessions.size();
    }

    private void acceptLoop() {
        while (!closed.get()) {
            final Socket localSocket;
            try {
                localSocket = listener.accept();
            } catch (IOException exception) {
                if (!closed.get()) close();
                return;
            }

            if (!localSocket.getInetAddress().isLoopbackAddress()
                    || !connectionSlots.tryAcquire()) {
                closeQuietly(localSocket);
                continue;
            }

            try {
                localSocket.setTcpNoDelay(true);
                workers.execute(() -> openSession(localSocket));
            } catch (IOException | RejectedExecutionException exception) {
                closeQuietly(localSocket);
                connectionSlots.release();
                if (exception instanceof RejectedExecutionException && !closed.get()) close();
            }
        }
    }

    private void openSession(Socket localSocket) {
        DuplexConnection remote = null;
        try {
            remote = connector.connect();
            if (remote == null) throw new IOException("Remote connector returned no stream.");
            Session session = new Session(localSocket, remote);
            sessions.add(session);
            if (closed.get()) {
                session.close();
                return;
            }
            session.start();
        } catch (Exception exception) {
            closeQuietly(localSocket);
            closeQuietly(remote);
            connectionSlots.release();
        }
    }

    private final class Session implements AutoCloseable {
        private final Socket localSocket;
        private final DuplexConnection remote;
        private final AtomicInteger finishedDirections = new AtomicInteger();
        private final AtomicBoolean ended = new AtomicBoolean();

        private Session(Socket localSocket, DuplexConnection remote) {
            this.localSocket = localSocket;
            this.remote = remote;
        }

        private void start() {
            try {
                workers.execute(() -> pump(localSocketInput(), remote.outputStream(),
                        remote::shutdownOutput));
                workers.execute(() -> pump(remote.inputStream(), localSocketOutput(),
                        localSocket::shutdownOutput));
            } catch (RuntimeException exception) {
                close();
            }
        }

        private InputStream localSocketInput() {
            try {
                return localSocket.getInputStream();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private OutputStream localSocketOutput() {
            try {
                return localSocket.getOutputStream();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private void pump(InputStream source, OutputStream destination,
                          IoAction shutdownDestination) {
            try {
                byte[] buffer = new byte[COPY_BUFFER_BYTES];
                int read;
                while ((read = source.read(buffer)) != -1) {
                    destination.write(buffer, 0, read);
                    destination.flush();
                }
                shutdownDestination.run();
            } catch (IOException | RuntimeException exception) {
                close();
            } finally {
                if (finishedDirections.incrementAndGet() == 2) close();
            }
        }

        @Override
        public void close() {
            if (!ended.compareAndSet(false, true)) return;
            closeQuietly(localSocket);
            closeQuietly(remote);
            sessions.remove(this);
            connectionSlots.release();
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        closeQuietly(listener);
        for (Session session : sessions) session.close();
        workers.shutdownNow();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Closing either half of a tunnel is best-effort; the other half is always closed too.
        }
    }
}
