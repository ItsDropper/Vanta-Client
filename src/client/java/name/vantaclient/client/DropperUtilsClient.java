package name.vantaclient.client;

import net.fabricmc.api.ClientModInitializer;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.FeatureRegistry;
import name.vantaclient.client.integration.BuiltInIntegration;
import name.vantaclient.client.init.Events;
import name.vantaclient.client.init.HudRegistration;
import name.vantaclient.client.init.Keybinds;

public class DropperUtilsClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {

		DropperUtilsConfig.load();
		FeatureRegistry.loadConfigs();
		BuiltInIntegration.register();

		Keybinds.register();
		HudRegistration.register();
		Events.register();
	}
}