package name.vantaclient.mixin;

import name.vantaclient.client.gui.VantaWorldShareScreen;
import name.vantaclient.mixin.accessor.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds an entry to Vanta's world-sharing screen from a singleplayer pause menu. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void vanta$addWorldSharingEntry(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.getSingleplayerServer() == null) return;

        Screen screen = (Screen) (Object) this;
        int buttonWidth = 112;
        int x = Math.max(6, screen.width - buttonWidth - 8);
        Button shareButton = Button.builder(Component.literal("Vanta Share"),
                        button -> client.setScreen(new VantaWorldShareScreen(screen)))
                .bounds(x, 8, buttonWidth, 20)
                .build();
        ((ScreenAccessor) (Object) this).vanta$addRenderableWidget(shareButton);
    }
}
