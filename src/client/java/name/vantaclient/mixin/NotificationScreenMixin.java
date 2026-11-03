package name.vantaclient.mixin;

import name.vantaclient.client.gui.VantaNotificationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class NotificationScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void vanta$renderNotifications(GuiGraphics graphics, int mouseX, int mouseY,
                                           float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) return;
        VantaNotificationManager.render(graphics,
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight());
    }
}
