package name.vantaclient.client.integration.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connects one already-authorized remote byte stream to the host's fixed loopback
 * Minecraft endpoint. The destination is never supplied by an untrusted peer.
 */
public final class TcpStreamTunnel implements AutoCloseable {
    private static final int BUFFER_BYTES = 16 * 1024;
    private static final AtomicInteger IDS = new AtomicInteger();

    private final Socket localSocket;
    private final LoopbackTcpBridge.DuplexConnection remote;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger finishedDirections = new AtomicInteger();

    private TcpStreamTunnel(Socket localSocket, LoopbackTcpBridge.DuplexConnection remote)
            throws IOException {
        this.localSocket = localSocket;
        this.remote = remote;
        try {
            Thread.ofVirtual().name("Vanta host tunnel " + IDS.incrementAndGet() + " up")
                    .start(() -> pump(localInput(), remote.outputStream(), remote::shutdownOutput));
            Thread.ofVirtual().name("Vanta host tunnel " + IDS.incrementAndGet() + " down")
                    .start(() -> pump(remote.inputStream(), localOutput(), localSocket::shutdownOutput));
        } catch (RuntimeException exception) {
            close();
            throw new IOException("Could not start the host-side tunnel.", exception);
        }
    }

    public static TcpStreamTunnel connectToLoopbackServer(
            int serverPort, LoopbackTcpBridge.DuplexConnection remote) throws IOException {
        if (serverPort < 1 || serverPort > 65_535) throw new IllegalArgumentException("Invalid server port.");
        if (remote == null) throw new IllegalArgumentException("Remote stream cannot be null.");
        Socket socket = new Socket();
        boolean connected = false;
        try {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort), 5_000);
            connected = true;
            return new TcpStreamTunnel(socket, remote);
        } finally {
            if (!connected) {
                try { socket.close(); } catch (IOException ignored) {}
                try { remote.close(); } catch (IOException ignored) {}
            }
        }
    }

    private InputStream localInput() {
        try {
            return localSocket.getInputStream();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private OutputStream localOutput() {
        try {
            return localSocket.getOutputStream();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void pump(InputStream source, OutputStream destination, IoAction shutdownDestination) {
        try {
            byte[] buffer = new byte[BUFFER_BYTES];
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
        if (!closed.compareAndSet(false, true)) return;
        try { localSocket.close(); } catch (IOException ignored) {}
        try { remote.close(); } catch (IOException ignored) {}
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }
}
