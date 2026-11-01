package name.vantaclient.client.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;
import name.vantaclient.client.gui.VantaIntegrationsScreen;

/** Optional Mod Menu entrypoint; Mod Menu is not a required runtime dependency. */
public final class VantaModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return VantaIntegrationsScreen::new;
    }

    public static Screen openModsScreen(Screen parent) {
        return ModMenuApi.createModsScreen(parent);
    }
}
