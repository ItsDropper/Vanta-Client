package name.vantaclient.api;

import name.vantaclient.api.hud.VantaHudElement;
import java.util.Objects;

/** A HUD element registered by a mod through {@link VantaClientApi}. */
public record RegisteredHud(String modId, VantaHudElement element) {
    public RegisteredHud {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(element, "element");
    }
}
