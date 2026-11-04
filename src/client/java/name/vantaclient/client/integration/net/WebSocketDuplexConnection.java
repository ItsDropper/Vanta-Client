package name.vantaclient.client.integration.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ordered, backpressured byte-stream adapter over the relay's binary WebSocket messages.
 * Each write becomes a bounded binary message; directional EOF is represented by the
 * relay's reserved VANTA_EOF text message.
 */
public final class WebSocketDuplexConnection implements LoopbackTcpBridge.DuplexConnection {
    private static final int MAX_MESSAGE_BYTES = 16 * 1024;
    private static final int MAX_QUEUED_MESSAGES = 128;
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final WebSocket socket;
    private final BlockingQueue<ReadEvent> incoming = new ArrayBlockingQueue<>(MAX_QUEUED_MESSAGES);
    private final AtomicBoolean outputClosed = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final InputStream inputStream = new RemoteInputStream();
    private final OutputStream outputStream = new RemoteOutputStream();
    private byte[] assemblingBinary;
    private int assemblingLength;
    private final StringBuilder assemblingText = new StringBuilder();
    private byte[] currentInput;
    private int currentInputOffset;

    private WebSocketDuplexConnection(WebSocket socket) {
        this.socket = socket;
    }

    public static WebSocketDuplexConnection connect(HttpClient client, URI endpoint,
                                                    String bearerToken) throws IOException {
        WebSocketDuplexConnection[] owner = new WebSocketDuplexConnection[1];
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                owner[0] = new WebSocketDuplexConnection(webSocket);
                webSocket.request(1);
            }

            @Override
            public synchronized CompletableFuture<?> onBinary(
                    WebSocket webSocket, ByteBuffer data, boolean last) {
                WebSocketDuplexConnection connection = owner[0];
                if (connection == null || connection.closed.get()) {
                    webSocket.abort();
                    return CompletableFuture.completedFuture(null);
                }
                int remaining = data.remaining();
                if (connection.assemblingLength + remaining > MAX_MESSAGE_BYTES) {
                    connection.fail(new IOException("Relay sent an oversized binary message."));
                    webSocket.abort();
                    return CompletableFuture.completedFuture(null);
                }
                if (connection.assemblingBinary == null) {
                    connection.assemblingBinary = new byte[MAX_MESSAGE_BYTES];
                }
                data.get(connection.assemblingBinary, connection.assemblingLength, remaining);
                connection.assemblingLength += remaining;
                if (last) {
                    byte[] message = java.util.Arrays.copyOf(
                            connection.assemblingBinary, connection.assemblingLength);
                    connection.assemblingBinary = null;
                    connection.assemblingLength = 0;
                    if (!connection.incoming.offer(ReadEvent.data(message))) {
                        connection.fail(new IOException("Relay receive queue is full."));
                        webSocket.abort();
                    }
                } else {
                    // A fragmented WebSocket message must continue assembling, but a
                    // completed message is not followed by another request until the
                    // Minecraft-side reader consumes the current buffer.
                    webSocket.request(1);
                }
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public synchronized CompletableFuture<?> onText(
                    WebSocket webSocket, CharSequence data, boolean last) {
                WebSocketDuplexConnection connection = owner[0];
                if (connection == null) {
                    webSocket.abort();
                    return CompletableFuture.completedFuture(null);
                }
                if (connection.assemblingText.length() + data.length() > 32) {
                    connection.fail(new IOException("Relay sent an invalid stream control message."));
                    webSocket.abort();
                    return CompletableFuture.completedFuture(null);
                }
                connection.assemblingText.append(data);
                if (last) {
                    String message = connection.assemblingText.toString();
                    connection.assemblingText.setLength(0);
                    if ("VANTA_EOF".equals(message)) {
                        connection.offerTerminal(ReadEvent.eof());
                    } else {
                        connection.fail(new IOException("Relay sent an unknown stream control message."));
                        webSocket.abort();
                    }
                } else {
                    webSocket.request(1);
                }
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                WebSocketDuplexConnection connection = owner[0];
                if (connection != null) connection.offerTerminal(ReadEvent.eof());
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                WebSocketDuplexConnection connection = owner[0];
                if (connection != null) connection.fail(error);
            }
        };

        try {
            WebSocket socket = client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + bearerToken)
                    .buildAsync(endpoint, listener)
                    .get(12, TimeUnit.SECONDS);
            WebSocketDuplexConnection connection = owner[0];
            if (connection == null) {
                socket.abort();
                throw new IOException("Relay stream did not initialize.");
            }
            return connection;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Relay stream connection was interrupted.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException("Could not open the relay data stream.", exception);
        }
    }

    @Override
    public InputStream inputStream() {
        return inputStream;
    }

    @Override
    public OutputStream outputStream() {
        return outputStream;
    }

    @Override
    public void shutdownOutput() throws IOException {
        if (!outputClosed.compareAndSet(false, true)) return;
        sendText("VANTA_EOF");
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        offerTerminal(ReadEvent.eof());
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "stream closed")
                    .get(2, TimeUnit.SECONDS);
        } catch (Exception exception) {
            socket.abort();
        }
    }

    private void sendBinary(byte[] bytes, int offset, int length) throws IOException {
        if (closed.get() || outputClosed.get()) throw new IOException("Relay stream output is closed.");
        byte[] copy = java.util.Arrays.copyOfRange(bytes, offset, offset + length);
        try {
            socket.sendBinary(ByteBuffer.wrap(copy), true).get(SEND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Relay write was interrupted.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException("Could not write to the relay stream.", exception);
        }
    }

    private void sendText(String value) throws IOException {
        try {
            socket.sendText(value, true).get(SEND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Relay stream close was interrupted.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException("Could not close the relay stream direction.", exception);
        }
    }

    private void fail(Throwable failure) {
        IOException error = failure instanceof IOException io
                ? io : new IOException("Relay data stream failed.", failure);
        offerTerminal(ReadEvent.error(error));
    }

    private void offerTerminal(ReadEvent event) {
        if (!incoming.offer(event)) {
            incoming.clear();
            incoming.offer(event);
        }
    }

    private final class RemoteInputStream extends InputStream {
        @Override
        public synchronized int read() throws IOException {
            byte[] one = new byte[1];
            int count = read(one, 0, 1);
            return count < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) throws IOException {
            if (bytes == null) throw new NullPointerException("bytes");
            if (offset < 0 || length < 0 || length > bytes.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) return 0;

            while (currentInput == null || currentInputOffset >= currentInput.length) {
                final ReadEvent event;
                try {
                    event = incoming.take();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Relay read was interrupted.", exception);
                }
                if (event.error != null) throw event.error;
                if (event.eof) return -1;
                currentInput = event.bytes;
                currentInputOffset = 0;
            }

            int count = Math.min(length, currentInput.length - currentInputOffset);
            System.arraycopy(currentInput, currentInputOffset, bytes, offset, count);
            currentInputOffset += count;
            if (currentInputOffset >= currentInput.length) {
                currentInput = null;
                currentInputOffset = 0;
                socket.request(1);
            }
            return count;
        }
    }

    private final class RemoteOutputStream extends OutputStream {
        @Override
        public synchronized void write(int value) throws IOException {
            byte[] one = {(byte) value};
            write(one, 0, 1);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            if (bytes == null) throw new NullPointerException("bytes");
            if (offset < 0 || length < 0 || length > bytes.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            if (closed.get() || outputClosed.get()) throw new IOException("Relay stream output is closed.");
            int remaining = length;
            int cursor = offset;
            while (remaining > 0) {
                int count = Math.min(MAX_MESSAGE_BYTES, remaining);
                sendBinary(bytes, cursor, count);
                cursor += count;
                remaining -= count;
            }
        }

        @Override
        public void close() throws IOException {
            shutdownOutput();
        }
    }

    private static final class ReadEvent {
        private final byte[] bytes;
        private final IOException error;
        private final boolean eof;

        private ReadEvent(byte[] bytes, IOException error, boolean eof) {
            this.bytes = bytes;
            this.error = error;
            this.eof = eof;
        }

        private static ReadEvent data(byte[] bytes) { return new ReadEvent(bytes, null, false); }
        private static ReadEvent eof() { return new ReadEvent(null, null, true); }
        private static ReadEvent error(IOException error) { return new ReadEvent(null, error, false); }
    }
}
