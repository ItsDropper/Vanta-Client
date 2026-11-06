package name.vantaclient.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import name.vantaclient.client.platform.ClientPlatform;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.FeatureRegistry;
import name.vantaclient.client.integration.BuiltInIntegration;
import name.vantaclient.client.init.Events;
import name.vantaclient.client.init.HudRegistration;
import name.vantaclient.client.init.Keybinds;
import name.vantaclient.client.init.FeatureKeybinds;
import name.vantaclient.client.init.VantaClientCommands;
import name.vantaclient.client.util.FarTerrainRenderer;

public class DropperUtilsClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {

		ClientPlatform.configure(FabricLoader.getInstance().getConfigDir(), true);
		DropperUtilsConfig.load();
		FeatureRegistry.loadConfigs();
		BuiltInIntegration.register();

		Keybinds.register();
		FeatureKeybinds.register();
		VantaClientCommands.register();
		HudRegistration.register();
		FarTerrainRenderer.register();
		Events.register();
	}
}