package name.vantaclient.client.integration.net;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exercises the loopback bridge independently of Minecraft's UI and relay service.
 * Worker-thread failures are captured and rethrown on the GameTest thread.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LoopbackTcpBridgeGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            try {
                verifyEchoAndWorkerFailurePropagation();
                verifyConnectionLimitAndCleanup();
                verifyConnectorFailureClosesClient();
                verifyCloseDuringConnectCleansPendingSocket();
                verifyHostTunnelRegistersSourcePortBeforeForwarding();
            } catch (Exception exception) {
                throw new AssertionError("Loopback TCP bridge GameTests failed.", exception);
            }
        });
    }

    private static void verifyEchoAndWorkerFailurePropagation() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (ServerSocket echoServer = new ServerSocket(0, 2, loopback)) {
            FutureTask<Void> echoTask = new FutureTask<>(() -> {
                try (Socket socket = echoServer.accept()) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = socket.getInputStream().read(buffer)) != -1) {
                        socket.getOutputStream().write(buffer, 0, read);
                        socket.getOutputStream().flush();
                    }
                    socket.shutdownOutput();
                }
                return null;
            });
            Thread echoWorker = new Thread(echoTask, "Vanta bridge echo test");
            echoWorker.setDaemon(true);
            echoWorker.start();

            try (LoopbackTcpBridge bridge = LoopbackTcpBridge.listen(() -> {
                Socket upstream = new Socket();
                upstream.connect(new InetSocketAddress(loopback, echoServer.getLocalPort()), 2_000);
                upstream.setSoTimeout(2_000);
                return LoopbackTcpBridge.fromSocket(upstream);
            }, 2)) {
                if (!bridge.address().getAddress().isLoopbackAddress()) {
                    throw new AssertionError("The tunnel listener must bind to loopback only: " + bridge.address());
                }

                byte[] payload = new byte[] {
                        0x00, 0x01, 0x7F, (byte) 0x80, (byte) 0xFF, 0x21, 0x42, 0x00
                };
                try (Socket client = new Socket()) {
                    client.connect(bridge.address(), 2_000);
                    client.setSoTimeout(2_000);
                    client.getOutputStream().write(payload);
                    client.shutdownOutput();
                    byte[] echoed = client.getInputStream().readNBytes(payload.length);
                    if (!Arrays.equals(payload, echoed)) {
                        throw new AssertionError("Bridge corrupted binary payload: " + Arrays.toString(echoed));
                    }
                    if (client.getInputStream().read() != -1) {
                        throw new AssertionError("Bridge did not propagate the remote half-close.");
                    }
                }
            }

            // FutureTask transports exceptions from the daemon worker to this test thread.
            echoTask.get(3, TimeUnit.SECONDS);
        }
    }

    private static void verifyConnectionLimitAndCleanup() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (ServerSocket upstreamServer = new ServerSocket(0, 2, loopback);
             LoopbackTcpBridge bridge = LoopbackTcpBridge.listen(() -> {
                 Socket upstream = new Socket();
                 upstream.connect(new InetSocketAddress(loopback, upstreamServer.getLocalPort()), 2_000);
                 return LoopbackTcpBridge.fromSocket(upstream);
             }, 1)) {
            FutureTask<Void> upstreamTask = new FutureTask<>(() -> {
                try (Socket upstream = upstreamServer.accept()) {
                    while (upstream.getInputStream().read() != -1) {
                        // Keep the first stream open until the local client disconnects.
                    }
                }
                return null;
            });
            Thread worker = new Thread(upstreamTask, "Vanta bridge limit upstream");
            worker.setDaemon(true);
            worker.start();

            try (Socket first = new Socket()) {
                first.connect(bridge.address(), 2_000);
                awaitActiveConnections(bridge, 1);

                try (Socket rejected = new Socket()) {
                    rejected.connect(bridge.address(), 2_000);
                    rejected.setSoTimeout(2_000);
                    if (rejected.getInputStream().read() != -1) {
                        throw new AssertionError("Bridge accepted more than its configured connection limit.");
                    }
                }
            }

            awaitActiveConnections(bridge, 0);
            upstreamTask.get(3, TimeUnit.SECONDS);
        }
    }

    private static void verifyConnectorFailureClosesClient() throws Exception {
        try (LoopbackTcpBridge bridge = LoopbackTcpBridge.listen(() -> {
            throw new IOException("expected connector failure");
        }, 1);
             Socket client = new Socket()) {
            client.connect(bridge.address(), 2_000);
            client.setSoTimeout(2_000);
            if (client.getInputStream().read() != -1) {
                throw new AssertionError("A failed remote connector must close its local client.");
            }
            awaitActiveConnections(bridge, 0);
        }
    }

    private static void verifyCloseDuringConnectCleansPendingSocket() throws Exception {
        CountDownLatch connectorEntered = new CountDownLatch(1);
        try (LoopbackTcpBridge bridge = LoopbackTcpBridge.listen(() -> {
            connectorEntered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Connector interrupted during bridge shutdown.", exception);
            }
            throw new IOException("Unreachable connector completion.");
        }, 1);
             Socket client = new Socket()) {
            client.connect(bridge.address(), 2_000);
            if (!connectorEntered.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Bridge did not start its remote connector.");
            }
            bridge.close();
            client.setSoTimeout(2_000);
            if (client.getInputStream().read() != -1) {
                throw new AssertionError("Closing the bridge must close sockets awaiting remote setup.");
            }
            if (bridge.activeConnections() != 0) {
                throw new AssertionError("Closed bridge retained active sessions.");
            }
        }
    }

    private static void verifyHostTunnelRegistersSourcePortBeforeForwarding() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        byte[] payload = new byte[] { 0x12, 0x34, 0x56, 0x78, (byte) 0x9A };
        AtomicInteger registeredPort = new AtomicInteger(-1);
        AtomicInteger acceptedPort = new AtomicInteger(-1);
        AtomicBoolean registrationFinished = new AtomicBoolean();
        CountDownLatch registrationEntered = new CountDownLatch(1);
        CountDownLatch allowRegistration = new CountDownLatch(1);
        CountDownLatch remotePayloadReceived = new CountDownLatch(1);

        try (ServerSocket minecraftServer = new ServerSocket(0, 1, loopback);
             ServerSocket remoteEchoServer = new ServerSocket(0, 1, loopback)) {
            FutureTask<Void> echoTask = new FutureTask<>(() -> {
                try (Socket echo = remoteEchoServer.accept()) {
                    echo.setSoTimeout(2_000);
                    int first = echo.getInputStream().read();
                    if (first < 0) {
                        throw new AssertionError("Host tunnel closed before forwarding the Minecraft payload.");
                    }
                    remotePayloadReceived.countDown();
                    byte[] receivedTail = echo.getInputStream().readNBytes(payload.length - 1);
                    byte[] received = new byte[payload.length];
                    received[0] = (byte) first;
                    System.arraycopy(receivedTail, 0, received, 1, receivedTail.length);
                    if (!Arrays.equals(payload, received)) {
                        throw new AssertionError("Host tunnel forwarded the wrong payload to its remote stream.");
                    }
                    if (!registrationFinished.get()) {
                        throw new AssertionError("Tunnel forwarded bytes before registering its source port.");
                    }
                    echo.getOutputStream().write(received);
                    echo.getOutputStream().flush();
                    echo.shutdownOutput();
                }
                return null;
            });
            FutureTask<Void> minecraftTask = new FutureTask<>(() -> {
                try (Socket accepted = minecraftServer.accept()) {
                    accepted.setSoTimeout(2_000);
                    acceptedPort.set(((InetSocketAddress) accepted.getRemoteSocketAddress()).getPort());
                    accepted.getOutputStream().write(payload);
                    accepted.getOutputStream().flush();
                    accepted.shutdownOutput();
                    byte[] returned = accepted.getInputStream().readNBytes(payload.length);
                    if (!Arrays.equals(payload, returned)) {
                        throw new AssertionError("Host tunnel did not return the remote stream payload.");
                    }
                }
                return null;
            });
            Thread echoWorker = new Thread(echoTask, "Vanta host tunnel echo test");
            Thread minecraftWorker = new Thread(minecraftTask, "Vanta integrated server port test");
            echoWorker.setDaemon(true);
            minecraftWorker.setDaemon(true);
            echoWorker.start();
            minecraftWorker.start();

            Socket remote = new Socket(loopback, remoteEchoServer.getLocalPort());
            FutureTask<TcpStreamTunnel> tunnelTask = new FutureTask<>(() ->
                    TcpStreamTunnel.connectToLoopbackServer(
                            minecraftServer.getLocalPort(), LoopbackTcpBridge.fromSocket(remote),
                            localPort -> {
                                registeredPort.set(localPort);
                                registrationEntered.countDown();
                                try {
                                    if (!allowRegistration.await(2, TimeUnit.SECONDS)) return false;
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    return false;
                                }
                                registrationFinished.set(true);
                                return true;
                            }, null));
            Thread tunnelWorker = new Thread(tunnelTask, "Vanta host tunnel registration test");
            tunnelWorker.setDaemon(true);
            tunnelWorker.start();

            if (!registrationEntered.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Host tunnel did not reach its pre-forwarding registration callback.");
            }
            if (remotePayloadReceived.await(150, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Host tunnel forwarded Minecraft bytes before registration completed.");
            }

            allowRegistration.countDown();
            try (TcpStreamTunnel tunnel = tunnelTask.get(3, TimeUnit.SECONDS)) {
                minecraftTask.get(3, TimeUnit.SECONDS);
                echoTask.get(3, TimeUnit.SECONDS);
                if (registeredPort.get() < 1 || registeredPort.get() > 65_535) {
                    throw new AssertionError("Host tunnel did not register a valid source port.");
                }
                if (acceptedPort.get() != registeredPort.get()) {
                    throw new AssertionError("Integrated server observed source port " + acceptedPort.get()
                            + " but the tunnel registered " + registeredPort.get() + ".");
                }
            }
        }
    }

    private static void awaitActiveConnections(LoopbackTcpBridge bridge, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (bridge.activeConnections() != expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        if (bridge.activeConnections() != expected) {
            throw new AssertionError("Expected " + expected + " active bridge connections, found "
                    + bridge.activeConnections() + ".");
        }
    }
}
