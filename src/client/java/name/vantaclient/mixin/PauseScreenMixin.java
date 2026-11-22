package name.vantaclient.mixin;

import name.vantaclient.client.gui.VantaPauseUi;
import name.vantaclient.client.gui.VantaWorldShareScreen;
import name.vantaclient.mixin.accessor.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Replaces the pause menu's look with Vanta's and adds world sharing to singleplayer worlds. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void vanta$styleMenu(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Screen screen = (Screen) (Object) this;
        ScreenAccessor accessor = (ScreenAccessor) (Object) this;

        List<AbstractButton> buttons = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof StringWidget label) {
                label.visible = false;
            } else if (child instanceof AbstractButton button && button.visible) {
                buttons.add(button);
            }
        }
        if (buttons.isEmpty()) return;

        String subtitle = "";
        if (client.getSingleplayerServer() != null) {
            subtitle = client.getSingleplayerServer().getWorldData().getLevelName();
            Button share = Button.builder(Component.literal("Vanta Share"),
                            button -> client.setScreen(new VantaWorldShareScreen(screen)))
                    .bounds(0, 0, 10, 10).build();
            accessor.vanta$addRenderableWidget(share);
            buttons.add(Math.max(1, buttons.size() - 1), share);
        } else if (client.getCurrentServer() != null) {
            subtitle = client.getCurrentServer().name;
        }
        accessor.vanta$addRenderableOnly(VantaPauseUi.create(screen, buttons, subtitle));
    }
}
