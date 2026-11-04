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
