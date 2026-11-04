package name.vantaclient.mixin;

import name.vantaclient.client.integration.net.VantaRemoteSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-checks remote-world identity when vanilla accepts a Mojang-signed player key.
 *
 * Integrated servers do not authenticate logins in the normal dedicated-server
 * login flow. The player chat-session update is separately verified by vanilla
 * against Mojang's services keys before the server installs the chat session.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RemoteWorldIdentityMixin {
    @Inject(method = "handleChatSessionUpdate", at = @At("TAIL"))
    private void vanta$onVerifiedChatSession(CallbackInfo callback) {
        ServerGamePacketListenerImpl handler =
                (ServerGamePacketListenerImpl) (Object) this;
        if (handler.player == null) return;

        var chatSession = handler.player.getChatSession();
        if (chatSession == null || chatSession.hasExpired()) return;

        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            VantaRemoteSessionManager.onHostPlayerJoined(handler, server);
        }
    }
}
