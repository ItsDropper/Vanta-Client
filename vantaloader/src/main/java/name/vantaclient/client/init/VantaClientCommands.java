package name.vantaclient.client.init;

import com.mojang.brigadier.arguments.StringArgumentType;
import name.vantaclient.client.feature.OgAnimationsFeature;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Local client commands for Vanta presentation settings. */
public final class VantaClientCommands {
    private VantaClientCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var og = ClientCommandManager.literal("og")
                    .executes(context -> {
                        context.getSource().sendFeedback(Component.literal(
                                "OG Animations: " + (OgAnimationsFeature.INSTANCE.isEnabled() ? "ON" : "OFF")
                                        + " | Profile: " + OgAnimationsFeature.getPresetLabel()
                                        + " | /vanta og toggle, /vanta og preset <classic|soft>"));
                        return 1;
                    })
                    .then(ClientCommandManager.literal("toggle").executes(context -> {
                        OgAnimationsFeature.INSTANCE.toggle();
                        OgAnimationsFeature.INSTANCE.saveConfig();
                        context.getSource().sendFeedback(Component.literal(
                                "OG Animations " + (OgAnimationsFeature.INSTANCE.isEnabled() ? "enabled" : "disabled")));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("on").executes(context -> {
                        OgAnimationsFeature.INSTANCE.setEnabled(true);
                        OgAnimationsFeature.INSTANCE.saveConfig();
                        context.getSource().sendFeedback(Component.literal("OG Animations enabled"));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("off").executes(context -> {
                        OgAnimationsFeature.INSTANCE.setEnabled(false);
                        OgAnimationsFeature.INSTANCE.saveConfig();
                        context.getSource().sendFeedback(Component.literal("OG Animations disabled"));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("preset")
                            .then(ClientCommandManager.argument("profile", StringArgumentType.word())
                                    .suggests((context, builder) ->
                                            SharedSuggestionProvider.suggest(List.of("classic", "soft"), builder))
                                    .executes(context -> {
                                        String profile = StringArgumentType.getString(context, "profile");
                                        if (profile.equalsIgnoreCase("classic")) {
                                            OgAnimationsFeature.setPreset(OgAnimationsFeature.Preset.CLASSIC_1_8);
                                        } else if (profile.equalsIgnoreCase("soft")) {
                                            OgAnimationsFeature.setPreset(OgAnimationsFeature.Preset.SOFT_LEGACY);
                                        } else {
                                            context.getSource().sendError(Component.literal(
                                                    "Unknown profile. Use classic or soft."));
                                            return 0;
                                        }
                                        context.getSource().sendFeedback(Component.literal(
                                                "OG Animations profile: " + OgAnimationsFeature.getPresetLabel()));
                                        return 1;
                                    })));
            dispatcher.register(ClientCommandManager.literal("vanta").then(og));
        });
    }
}
