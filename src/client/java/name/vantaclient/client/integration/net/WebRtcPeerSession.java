package name.vantaclient.client.integration.net;

import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelInit;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import dev.onvoid.webrtc.RTCDataChannelState;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCIceTransportPolicy;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import dev.onvoid.webrtc.RTCCandidatePairChangeEvent;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCOfferOptions;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A single Minecraft byte stream carried by one reliable, ordered WebRTC
 * DataChannel. SDP and ICE travel over the authenticated Vanta signalling
 * socket; gameplay bytes do not pass through that signalling service.
 *
 * The DataChannel uses small binary frames so TCP half-close can be represented
 * without closing the reverse direction. Receive queues are bounded and send
 * backpressure is tied to WebRTC's buffered amount.
 */
public final class WebRtcPeerSession implements LoopbackTcpBridge.DuplexConnection {
    private static final String CHANNEL_LABEL = "vanta-minecraft-stream";
    private static final int FRAME_DATA = 1;
    private static final int FRAME_FIN = 2;
    private static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private static final int MAX_QUEUED_FRAMES = 256;
    private static final long MAX_BUFFERED_BYTES = 1024L * 1024L;
    private static final Duration SIGNAL_READY_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration DESCRIPTION_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration PEER_TIMEOUT = Duration.ofSeconds(75);
    private static final byte[] EOF_FRAME = new byte[0];

    private final PeerConnectionFactory factory;
    private final RTCPeerConnection peer;
    private final VantaRelayClient.SignalConnection signalling;
    private final ExecutorService signallingWriter;
    private final ExecutorService signallingReader;
    private final ArrayBlockingQueue<byte[]> inbound = new ArrayBlockingQueue<>(MAX_QUEUED_FRAMES);
    private final CountDownLatch channelOpen = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean outputFinished = new AtomicBoolean();
    private final AtomicBoolean inputFinished = new AtomicBoolean();
    private final AtomicBoolean usingTurnRelay = new AtomicBoolean();
    private final AtomicBoolean signallingClosed = new AtomicBoolean();
    private final AtomicReference<IOException> failure = new AtomicReference<>();
    private final Object signallingLock = new Object();
    private final Object sendBackpressure = new Object();
    private final Queue<VantaRelayClient.RelaySignal> pendingLocalCandidates = new ArrayDeque<>();
    private final Queue<VantaRelayClient.RelaySignal> pendingRemoteCandidates = new ArrayDeque<>();

    private volatile RTCDataChannel channel;
    private volatile boolean localDescriptionPublished;
    private volatile boolean remoteDescriptionApplied;
    private byte[] currentInput;
    private int currentInputOffset;

    private final InputStream inputStream = new InputStream() {
        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int count = read(single, 0, 1);
            return count < 0 ? -1 : single[0] & 0xff;
        }

        @Override
        public int read(byte[] destination, int offset, int length) throws IOException {
            if (destination == null) throw new NullPointerException("destination");
            if (offset < 0 || length < 0 || length > destination.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) return 0;

            while (currentInput == null || currentInputOffset >= currentInput.length) {
                IOException error = failure.get();
                if (error != null) throw error;
                if (inputFinished.get() && inbound.isEmpty()) return -1;
                final byte[] next;
                try {
                    next = inbound.take();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while reading the WebRTC stream.", exception);
                }
                if (next.length == 0) {
                    inputFinished.set(true);
                    return -1;
                }
                currentInput = next;
                currentInputOffset = 0;
            }

            int copied = Math.min(length, currentInput.length - currentInputOffset);
            System.arraycopy(currentInput, currentInputOffset, destination, offset, copied);
            currentInputOffset += copied;
            if (currentInputOffset >= currentInput.length) {
                currentInput = null;
                currentInputOffset = 0;
            }
            return copied;
        }

        @Override
        public void close() throws IOException {
            WebRtcPeerSession.this.close();
        }
    };

    private final OutputStream outputStream = new OutputStream() {
        @Override
        public void write(int value) throws IOException {
            byte[] single = {(byte) value};
            write(single, 0, 1);
        }

        @Override
        public void write(byte[] source, int offset, int length) throws IOException {
            if (source == null) throw new NullPointerException("source");
            if (offset < 0 || length < 0 || length > source.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) return;
            if (outputFinished.get()) throw new IOException("The WebRTC stream output is already closed.");

            int remaining = length;
            int cursor = offset;
            while (remaining > 0) {
                int count = Math.min(remaining, MAX_PAYLOAD_BYTES);
                byte[] frame = new byte[count + 1];
                frame[0] = FRAME_DATA;
                System.arraycopy(source, cursor, frame, 1, count);
                sendFrame(frame);
                cursor += count;
                remaining -= count;
            }
        }

        @Override
        public void flush() throws IOException {
            ensureOpen();
        }

        @Override
        public void close() throws IOException {
            shutdownOutput();
        }
    };

    private WebRtcPeerSession(PeerConnectionFactory factory, RTCPeerConnection peer,
                              VantaRelayClient.SignalConnection signalling) {
        this.factory = factory;
        this.peer = peer;
        this.signalling = signalling;
        this.signallingWriter = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Vanta WebRTC signalling writer");
            thread.setDaemon(true);
            return thread;
        });
        this.signallingReader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Vanta WebRTC signalling reader");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static WebRtcPeerSession connectHost(
            VantaRelayClient relay, VantaRelayClient.Room room, String requestId,
            List<VantaRelayClient.IceServerConfig> iceServers, boolean relayOnly) throws IOException {
        if (relay == null || room == null) throw new IllegalArgumentException("Host relay session is required.");
        VantaRelayClient.SignalConnection signal = relay.connectHostSignal(room, requestId);
        return connect(true, signal, iceServers, relayOnly);
    }

    public static WebRtcPeerSession connectGuest(
            VantaRelayClient relay, VantaRelayClient.JoinTicket ticket,
            List<VantaRelayClient.IceServerConfig> iceServers, boolean relayOnly) throws IOException {
        if (relay == null || ticket == null) throw new IllegalArgumentException("Guest relay ticket is required.");
        VantaRelayClient.SignalConnection signal = relay.connectGuestSignal(ticket);
        return connect(false, signal, iceServers, relayOnly);
    }

    private static WebRtcPeerSession connect(boolean hostSide,
                                             VantaRelayClient.SignalConnection signal,
                                             List<VantaRelayClient.IceServerConfig> iceServers,
                                             boolean relayOnly)
            throws IOException {
        PeerConnectionFactory factory = null;
        RTCPeerConnection peer = null;
        WebRtcPeerSession session = null;
        try {
            if (iceServers == null || iceServers.isEmpty()) {
                throw new IOException("The relay has no STUN/TURN servers configured for Internet hosting.");
            }
            if (relayOnly && iceServers.stream().flatMap(server -> server.urls().stream())
                    .noneMatch(url -> url.startsWith("turn:") || url.startsWith("turns:"))) {
                throw new IOException("Maximum IP privacy requires a configured TURN relay.");
            }
            signal.awaitReady(SIGNAL_READY_TIMEOUT);
            factory = new PeerConnectionFactory();
            AtomicReference<WebRtcPeerSession> holder = new AtomicReference<>();
            PeerConnectionObserver observer = new PeerConnectionObserver() {
                @Override
                public void onIceCandidate(RTCIceCandidate candidate) {
                    WebRtcPeerSession current = holder.get();
                    if (current != null) current.queueLocalCandidate(candidate);
                }

                @Override
                public void onDataChannel(RTCDataChannel dataChannel) {
                    WebRtcPeerSession current = holder.get();
                    if (current != null) current.installDataChannel(dataChannel);
                    else closeChannel(dataChannel);
                }

                @Override
                public void onConnectionChange(RTCPeerConnectionState state) {
                    WebRtcPeerSession current = holder.get();
                    if (current != null && (state == RTCPeerConnectionState.FAILED
                            || state == RTCPeerConnectionState.CLOSED)) {
                        current.fail(new IOException("The WebRTC peer connection failed."));
                    }
                }

                @Override
                public void onSelectedCandidatePairChanged(RTCCandidatePairChangeEvent event) {
                    WebRtcPeerSession current = holder.get();
                    if (current != null && event != null) {
                        // ICE may switch candidate pairs after the initial connection.
                        // Reflect the currently selected pair instead of latching "TURN"
                        // forever after the first relay-backed pair.
                        current.usingTurnRelay.set(isRelayCandidate(event.getLocal())
                                || isRelayCandidate(event.getRemote()));
                    }
                }
            };

            RTCConfiguration configuration = new RTCConfiguration();
            configuration.iceTransportPolicy = relayOnly
                    ? RTCIceTransportPolicy.RELAY : RTCIceTransportPolicy.ALL;
            for (VantaRelayClient.IceServerConfig configured : iceServers) {
                RTCIceServer server = new RTCIceServer();
                server.urls.addAll(configured.urls());
                server.username = configured.username();
                server.password = configured.credential();
                configuration.iceServers.add(server);
            }
            peer = factory.createPeerConnection(configuration, observer);
            if (peer == null) throw new IOException("WebRTC could not create a peer connection.");
            session = new WebRtcPeerSession(factory, peer, signal);
            holder.set(session);

            if (hostSide) {
                RTCDataChannelInit options = new RTCDataChannelInit();
                options.ordered = true;
                options.maxPacketLifeTime = -1;
                options.maxRetransmits = -1;
                RTCDataChannel localChannel = peer.createDataChannel(CHANNEL_LABEL, options);
                if (localChannel == null) throw new IOException("WebRTC could not create a data channel.");
                session.installDataChannel(localChannel);
                session.negotiateHost();
            } else {
                session.negotiateGuest();
            }
            return session;
        } catch (Exception | LinkageError exception) {
            if (session != null) {
                session.close();
            } else {
                if (peer != null) {
                    try { peer.close(); } catch (RuntimeException ignored) {}
                }
                if (factory != null) {
                    try { factory.dispose(); } catch (RuntimeException ignored) {}
                }
                closeQuietly(signal);
            }
            if (exception instanceof IOException io) throw io;
            throw new IOException("Could not establish a WebRTC peer connection: "
                    + safeMessage(exception), exception);
        }
    }

    private void negotiateHost() throws IOException {
        CompletableFuture<RTCSessionDescription> offer = new CompletableFuture<>();
        peer.createOffer(new RTCOfferOptions(), new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription description) {
                offer.complete(description);
            }

            @Override
            public void onFailure(String error) {
                offer.completeExceptionally(new IOException("Could not create WebRTC offer: " + error));
            }
        });
        RTCSessionDescription description = await(offer, DESCRIPTION_TIMEOUT, "WebRTC offer");
        setLocalDescription(description);
        publishLocalDescription(description, true);
        awaitPeerChannel();
    }

    private void negotiateGuest() throws IOException {
        awaitPeerChannel();
    }

    private void awaitPeerChannel() throws IOException {
        long deadline = System.nanoTime() + PEER_TIMEOUT.toNanos();
        while (channelOpen.getCount() != 0 && System.nanoTime() < deadline) {
            IOException error = failure.get();
            if (error != null) throw error;

            VantaRelayClient.RelaySignal message;
            try {
                message = signalling.receive(Duration.ofMillis(250));
            } catch (IOException exception) {
                if (signalling.isOpen() && exception.getMessage() != null
                        && exception.getMessage().contains("Timed out waiting")) {
                    continue;
                }
                throw exception;
            }

            switch (message.type()) {
                case "ice" -> applyRemoteCandidate(message);
                case "answer" -> {
                    if (peer.getRemoteDescription() != null) {
                        throw new IOException("The relay sent a duplicate WebRTC answer.");
                    }
                    setRemoteDescription(new RTCSessionDescription(RTCSdpType.ANSWER, message.sdp()));
                    flushRemoteCandidates();
                }
                case "offer" -> {
                    if (peer.getRemoteDescription() != null) {
                        throw new IOException("The relay sent a duplicate WebRTC offer.");
                    }
                    setRemoteDescription(new RTCSessionDescription(RTCSdpType.OFFER, message.sdp()));
                    flushRemoteCandidates();
                    CompletableFuture<RTCSessionDescription> answer = new CompletableFuture<>();
                    peer.createAnswer(new RTCAnswerOptions(), new CreateSessionDescriptionObserver() {
                        @Override
                        public void onSuccess(RTCSessionDescription description) {
                            answer.complete(description);
                        }

                        @Override
                        public void onFailure(String error) {
                            answer.completeExceptionally(new IOException(
                                    "Could not create WebRTC answer: " + error));
                        }
                    });
                    RTCSessionDescription answerDescription =
                            await(answer, DESCRIPTION_TIMEOUT, "WebRTC answer");
                    setLocalDescription(answerDescription);
                    publishLocalDescription(answerDescription, false);
                }
                default -> throw new IOException("The relay sent an unexpected WebRTC message.");
            }
        }

        IOException error = failure.get();
        if (error != null) throw error;
        if (channelOpen.getCount() != 0) {
            throw new IOException("WebRTC did not establish a data channel before the connection timed out.");
        }
        startSignallingReader();
    }

    /**
     * ICE gathering can continue after the SCTP DataChannel opens. Keep draining
     * trickled candidates for the lifetime of the peer so the relay's bounded
     * signalling queue cannot fill and ICE can react to candidate-pair changes.
     */
    private void startSignallingReader() {
        signallingReader.execute(() -> {
            while (!closed.get() && !signallingClosed.get()) {
                try {
                    VantaRelayClient.RelaySignal message =
                            signalling.receive(Duration.ofSeconds(1));
                    if ("ice".equals(message.type())) {
                        applyRemoteCandidate(message);
                    } else {
                        fail(new IOException("The relay sent an unexpected post-negotiation signalling message."));
                        return;
                    }
                } catch (IOException exception) {
                    if (closed.get() || signallingClosed.get()) return;
                    String message = exception.getMessage();
                    if (message != null && message.contains("Timed out waiting")) continue;
                    fail(exception);
                    return;
                }
            }
        });
    }

    private void publishLocalDescription(RTCSessionDescription description, boolean hostSide)
            throws IOException {
        if (description == null || description.sdp == null || description.sdp.isBlank()) {
            throw new IOException("WebRTC produced an empty local session description.");
        }
        if (hostSide) signalling.sendOffer(description.sdp);
        else signalling.sendAnswer(description.sdp);

        List<VantaRelayClient.RelaySignal> queued;
        synchronized (signallingLock) {
            localDescriptionPublished = true;
            queued = new ArrayList<>(pendingLocalCandidates);
            pendingLocalCandidates.clear();
        }
        queued.forEach(this::sendCandidateAsync);
    }

    private void queueLocalCandidate(RTCIceCandidate candidate) {
        if (signallingClosed.get()) return;
        if (candidate == null || candidate.sdp == null || !candidate.sdp.startsWith("candidate:")) return;
        String mid = candidate.sdpMid == null || candidate.sdpMid.isBlank()
                ? "0" : candidate.sdpMid;
        int line = candidate.sdpMLineIndex < 0 ? 0 : candidate.sdpMLineIndex;
        VantaRelayClient.RelaySignal message = new VantaRelayClient.RelaySignal(
                "ice", null, candidate.sdp, mid, line);
        synchronized (signallingLock) {
            if (!localDescriptionPublished) {
                pendingLocalCandidates.add(message);
                return;
            }
        }
        sendCandidateAsync(message);
    }

    private void sendCandidateAsync(VantaRelayClient.RelaySignal candidate) {
        if (closed.get() || signallingClosed.get()) return;
        try {
            signallingWriter.execute(() -> {
                if (closed.get() || signallingClosed.get()) return;
                try {
                    signalling.sendIce(candidate.candidate(), candidate.sdpMid(),
                            candidate.sdpMLineIndex());
                } catch (IOException exception) {
                    if (!closed.get()) fail(exception);
                }
            });
        } catch (RuntimeException exception) {
            if (!closed.get()) fail(new IOException("Could not queue an ICE candidate.", exception));
        }
    }

    private void applyRemoteCandidate(VantaRelayClient.RelaySignal message) throws IOException {
        synchronized (signallingLock) {
            if (!remoteDescriptionApplied) {
                pendingRemoteCandidates.add(message);
                return;
            }
        }
        addRemoteCandidate(message);
    }

    private void flushRemoteCandidates() throws IOException {
        List<VantaRelayClient.RelaySignal> queued;
        synchronized (signallingLock) {
            remoteDescriptionApplied = true;
            queued = new ArrayList<>(pendingRemoteCandidates);
            pendingRemoteCandidates.clear();
        }
        for (VantaRelayClient.RelaySignal candidate : queued) addRemoteCandidate(candidate);
    }

    private void addRemoteCandidate(VantaRelayClient.RelaySignal message) throws IOException {
        try {
            peer.addIceCandidate(new RTCIceCandidate(
                    message.sdpMid(), message.sdpMLineIndex(), message.candidate()));
        } catch (RuntimeException exception) {
            throw new IOException("WebRTC rejected a remote ICE candidate.", exception);
        }
    }

    private void setLocalDescription(RTCSessionDescription description) throws IOException {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        peer.setLocalDescription(description, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                completion.complete(null);
            }

            @Override
            public void onFailure(String error) {
                completion.completeExceptionally(
                        new IOException("Could not set local WebRTC description: " + error));
            }
        });
        await(completion, DESCRIPTION_TIMEOUT, "setting local WebRTC description");
    }

    private void setRemoteDescription(RTCSessionDescription description) throws IOException {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        peer.setRemoteDescription(description, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                completion.complete(null);
            }

            @Override
            public void onFailure(String error) {
                completion.completeExceptionally(
                        new IOException("Could not set remote WebRTC description: " + error));
            }
        });
        await(completion, DESCRIPTION_TIMEOUT, "setting remote WebRTC description");
    }

    private static <T> T await(CompletableFuture<T> future, Duration timeout, String operation)
            throws IOException {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while " + operation + ".", exception);
        } catch (ExecutionException | TimeoutException exception) {
            Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                    ? exception.getCause() : exception;
            if (cause instanceof IOException io) throw io;
            throw new IOException("Timed out or failed while " + operation + ".", cause);
        }
    }

    private void installDataChannel(RTCDataChannel selected) {
        if (selected == null || closed.get()) {
            closeChannel(selected);
            return;
        }
        if (!CHANNEL_LABEL.equals(selected.getLabel())
                || !selected.isReliable() || !selected.isOrdered()) {
            closeChannel(selected);
            fail(new IOException("The remote peer offered an incompatible WebRTC data channel."));
            return;
        }
        synchronized (this) {
            if (channel != null && channel != selected) {
                closeChannel(selected);
                fail(new IOException("The remote peer opened an unexpected second data channel."));
                return;
            }
            channel = selected;
        }
        selected.registerObserver(new RTCDataChannelObserver() {
            @Override
            public void onBufferedAmountChange(long sentDataSize) {
                synchronized (sendBackpressure) {
                    sendBackpressure.notifyAll();
                }
            }

            @Override
            public void onStateChange() {
                RTCDataChannelState state = selected.getState();
                if (state == RTCDataChannelState.OPEN) {
                    channelOpen.countDown();
                } else if (state == RTCDataChannelState.CLOSED && !closed.get()
                        && !inputFinished.get()) {
                    fail(new IOException("The remote WebRTC data channel closed."));
                }
            }

            @Override
            public void onMessage(RTCDataChannelBuffer buffer) {
                receiveFrame(buffer);
            }
        });
        if (selected.getState() == RTCDataChannelState.OPEN) channelOpen.countDown();
    }

    private void receiveFrame(RTCDataChannelBuffer buffer) {
        if (closed.get() || buffer == null || !buffer.binary || buffer.data == null) {
            if (!closed.get()) fail(new IOException("The WebRTC peer sent a non-binary stream frame."));
            return;
        }
        ByteBuffer data = buffer.data.duplicate();
        if (!data.hasRemaining()) {
            fail(new IOException("The WebRTC peer sent an empty stream frame."));
            return;
        }
        int frameType = data.get() & 0xff;
        if (frameType == FRAME_DATA) {
            int remaining = data.remaining();
            if (remaining < 1 || remaining > MAX_PAYLOAD_BYTES) {
                fail(new IOException("The WebRTC peer sent an invalid data frame size."));
                return;
            }
            byte[] payload = new byte[remaining];
            data.get(payload);
            if (!inbound.offer(payload)) {
                fail(new IOException("The WebRTC receive queue is full."));
            }
        } else if (frameType == FRAME_FIN && !data.hasRemaining()) {
            if (inputFinished.compareAndSet(false, true) && !inbound.offer(EOF_FRAME)) {
                fail(new IOException("The WebRTC receive queue could not accept stream EOF."));
            }
        } else {
            fail(new IOException("The WebRTC peer sent an invalid stream control frame."));
        }
    }

    private void sendFrame(byte[] frame) throws IOException {
        ensureOpen();
        RTCDataChannel selected = channel;
        if (selected == null || selected.getState() != RTCDataChannelState.OPEN) {
            throw new IOException("The WebRTC data channel is not open.");
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        synchronized (sendBackpressure) {
            while (selected.getBufferedAmount() > MAX_BUFFERED_BYTES) {
                IOException error = failure.get();
                if (error != null) throw error;
                if (closed.get()) throw new IOException("The WebRTC stream is closed.");
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IOException("The WebRTC send queue stayed full for too long.");
                try {
                    TimeUnit.NANOSECONDS.timedWait(sendBackpressure,
                            Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for WebRTC send capacity.", exception);
                }
            }
        }

        try {
            selected.send(new RTCDataChannelBuffer(ByteBuffer.wrap(frame), true));
        } catch (Exception exception) {
            throw new IOException("Could not send a WebRTC data frame.", exception);
        }
    }

    @Override
    public void shutdownOutput() throws IOException {
        if (!outputFinished.compareAndSet(false, true)) return;
        sendFrame(new byte[]{FRAME_FIN});
    }

    private void ensureOpen() throws IOException {
        IOException error = failure.get();
        if (error != null) throw error;
        if (closed.get()) throw new IOException("The WebRTC stream is closed.");
    }

    private void fail(IOException error) {
        if (!failure.compareAndSet(null, error)) return;
        inbound.clear();
        inbound.offer(EOF_FRAME);
        synchronized (sendBackpressure) {
            sendBackpressure.notifyAll();
        }
    }

    private void closeSignalling() {
        if (!signallingClosed.compareAndSet(false, true)) return;
        try {
            signalling.close();
        } catch (RuntimeException ignored) {
            // The DataChannel owns the live byte stream after negotiation.
        }
        signallingWriter.shutdown();
        signallingReader.shutdownNow();
    }

    public boolean isUsingTurnRelay() {
        return usingTurnRelay.get();
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
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        inputFinished.set(true);
        inbound.clear();
        inbound.offer(EOF_FRAME);
        closeSignalling();
        signallingWriter.shutdownNow();
        signallingReader.shutdownNow();
        closeChannel(channel);
        try {
            peer.close();
        } catch (RuntimeException ignored) {
            // Continue releasing the factory even if the peer already closed.
        }
        try {
            factory.dispose();
        } catch (RuntimeException ignored) {
            // Native teardown is best-effort after the stream has been closed.
        }
        synchronized (sendBackpressure) {
            sendBackpressure.notifyAll();
        }
    }

    private static boolean isRelayCandidate(RTCIceCandidate candidate) {
        return candidate != null && candidate.sdp != null && candidate.sdp.contains(" typ relay");
    }

    private static void closeChannel(RTCDataChannel channel) {
        if (channel == null) return;
        try {
            channel.unregisterObserver();
        } catch (RuntimeException ignored) {}
        try {
            channel.close();
        } catch (RuntimeException ignored) {}
        try {
            channel.dispose();
        } catch (RuntimeException ignored) {}
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {}
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.isBlank()) return "native WebRTC initialization failed";
        message = message.replaceAll("[\\r\\n\\t]", " ").trim();
        return message.length() > 160 ? message.substring(0, 160) : message;
    }
}
