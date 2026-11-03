package name.vantaclient.mixin;

import name.vantaclient.client.hud.ClickRateTracker;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void vanta$trackClicks(long window, int button, int action, int modifiers,
                                   CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            ClickRateTracker.recordLeftClick();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            ClickRateTracker.recordRightClick();
        }
    }
}
