package name.vantaclient.mixin;

import name.vantaclient.client.hud.ClickRateTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    /**
     * 1.21.11 receives a MouseButtonInfo record rather than the old raw
     * (button, action, modifiers) callback arguments.
     */
    @Inject(
            method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",
            at = @At("HEAD")
    )
    private void vanta$trackClicks(long window, MouseButtonInfo buttonInfo, int action,
                                   CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;

        // Ignore clicks handled by menus, inventories, chat, and other screens.
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null) return;

        int button = buttonInfo.button();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            ClickRateTracker.recordLeftClick();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            ClickRateTracker.recordRightClick();
        }
    }
}
