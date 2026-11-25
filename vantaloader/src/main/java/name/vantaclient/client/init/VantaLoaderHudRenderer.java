package name.vantaclient.client.init;

import dev.itsdropper.vantaloader.api.HudRenderContext;
import name.vantaclient.client.feature.*;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.hudeditor.HudManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Renders the existing HUD layout through VMA's loader-neutral draw context. */
public final class VantaLoaderHudRenderer {
    private VantaLoaderHudRenderer() {}

    public static void render(HudRenderContext graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        for (HudElement element : HudManager.getActiveElements()) {
            String value = valueFor(element.getClass().getSimpleName(), client);
            if (value == null || value.isBlank()) continue;
            int x = Math.max(0, element.getX());
            int y = Math.max(0, element.getY());
            int width = Math.min(graphics.width() - x, Math.max(element.getScaledWidth(), value.length() * 6 + 8));
            if (width <= 0 || y >= graphics.height()) continue;
            int height = Math.min(graphics.height() - y, Math.max(10, element.getScaledHeight()));
            if (height <= 0) continue;
            graphics.fillRect(x - 3, y - 2, x + width + 3, y + height + 2, 0x800B101A);
            graphics.drawText(value, x, y, 0xFFFFFFFF);
        }
    }

    private static String valueFor(String element, Minecraft client) {
        return switch (element) {
            case "FpsHudElement" -> FpsHudFeature.INSTANCE.isEnabled() ? "FPS: " + client.getFps() : null;
            case "PingHudElement" -> {
                if (!PingHudFeature.INSTANCE.isEnabled() || client.getConnection() == null) yield null;
                var info = client.getConnection().getPlayerInfo(client.player.getUUID());
                yield info == null ? "Ping: --" : "Ping: " + info.getLatency() + " ms";
            }
            case "DebugHudElement" -> DebugHudFeature.INSTANCE.isEnabled()
                    ? String.format(Locale.ROOT, "XYZ: %.1f / %.1f / %.1f",
                        client.player.getX(), client.player.getY(), client.player.getZ()) : null;
            case "ArmorHudElement" -> {
                if (!ArmorHudFeature.INSTANCE.isEnabled()) yield null;
                StringBuilder armor = new StringBuilder("Armor: ");
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack stack = client.player.getItemBySlot(slot);
                    if (!stack.isEmpty()) {
                        if (armor.length() > 8) armor.append(" | ");
                        armor.append(stack.getHoverName().getString()).append(' ')
                                .append(stack.getMaxDamage() == 0 ? "∞"
                                        : Math.max(0, 100 - stack.getDamageValue() * 100 / stack.getMaxDamage()) + "%");
                    }
                }
                yield armor.length() == 8 ? null : armor.toString();
            }
            case "KeystrokesHudElement" -> KeystrokesFeature.INSTANCE.isEnabled()
                    ? "W " + key(client.options.keyUp.isDown()) + "  A " + key(client.options.keyLeft.isDown())
                        + "  S " + key(client.options.keyDown.isDown()) + "  D " + key(client.options.keyRight.isDown())
                    : null;
            default -> null;
        };
    }

    private static String key(boolean down) { return down ? "■" : "□"; }
}
