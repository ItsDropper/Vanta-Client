package name.vantaclient.mixin;

import name.vantaclient.client.feature.ChatTimestampsFeature;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    private static final DateTimeFormatter VANTA_TIME =
            DateTimeFormatter.ofPattern("HH:mm");

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component vanta$addTimestamp(Component message) {
        if (!ChatTimestampsFeature.INSTANCE.isEnabled()) return message;
        Component timestamp = Component.literal("[" + LocalTime.now().format(VANTA_TIME) + "] ")
                .withStyle(ChatFormatting.GRAY);
        return timestamp.copy().append(message);
    }
}
