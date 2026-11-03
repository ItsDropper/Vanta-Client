package name.vantaclient.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tick-rate snapshot of HUD data that otherwise gets queried or formatted
 * on every rendered frame. Only used by the opt-in Performance Optimizer.
 */
public final class HudPerformanceCache {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };
    private static final ItemStack[] ARMOR_STACKS = {
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
    };
    private static final int[] ARMOR_DURABILITY = new int[4];
    private static final int[] ARMOR_COLORS = new int[4];
    private static final int[] ARMOR_BAR_WIDTHS = new int[4];
    private static final String[] ARMOR_DURABILITY_TEXT = {"0", "0", "0", "0"};

    private static int totemCount;
    private static String pingText = "PING  --";
    private static String cpsText = "CPS  0 | 0";
    private static String fpsText = "FPS  0";

    private HudPerformanceCache() {}

    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            for (int i = 0; i < ARMOR_STACKS.length; i++) {
                ARMOR_STACKS[i] = ItemStack.EMPTY;
                ARMOR_DURABILITY[i] = 0;
                ARMOR_COLORS[i] = 0xFF555555;
                ARMOR_BAR_WIDTHS[i] = 0;
                ARMOR_DURABILITY_TEXT[i] = "0";
            }
            totemCount = 0;
            pingText = "PING  --";
            cpsText = "CPS  0 | 0";
            fpsText = "FPS  " + mc.getFps();
            return;
        }

        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            ItemStack stack = mc.player.getItemBySlot(ARMOR_SLOTS[i]);
            ARMOR_STACKS[i] = stack;
            if (stack.isEmpty() || stack.getMaxDamage() <= 0) {
                ARMOR_DURABILITY[i] = 0;
                ARMOR_COLORS[i] = 0xFF555555;
                ARMOR_BAR_WIDTHS[i] = 0;
                ARMOR_DURABILITY_TEXT[i] = "0";
                continue;
            }

            int maxDamage = stack.getMaxDamage();
            int durability = maxDamage - stack.getDamageValue();
            float percent = (float) durability / maxDamage;
            ARMOR_DURABILITY[i] = durability;
            ARMOR_COLORS[i] = percent > 0.5f ? 0xFF00FF00
                    : percent > 0.25f ? 0xFFFFFF00 : 0xFFFF0000;
            ARMOR_BAR_WIDTHS[i] = (int) (16.0f * percent);
            ARMOR_DURABILITY_TEXT[i] = Integer.toString(durability);
        }

        totemCount = mc.player.getInventory().countItem(Items.TOTEM_OF_UNDYING);

        int ping = -1;
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) ping = info.getLatency();
        }
        pingText = ping < 0 ? "PING  --" : "PING  " + ping + " ms";
        cpsText = "CPS  " + ClickRateTracker.getLeftCps()
                + " | " + ClickRateTracker.getRightCps();
        fpsText = "FPS  " + mc.getFps();
    }

    public static ItemStack getArmorStack(int slot) { return ARMOR_STACKS[slot]; }
    public static int getArmorDurability(int slot) { return ARMOR_DURABILITY[slot]; }
    public static int getArmorColor(int slot) { return ARMOR_COLORS[slot]; }
    public static int getArmorBarWidth(int slot) { return ARMOR_BAR_WIDTHS[slot]; }
    public static String getArmorDurabilityText(int slot) { return ARMOR_DURABILITY_TEXT[slot]; }
    public static int getTotemCount() { return totemCount; }
    public static String getPingText() { return pingText; }
    public static String getCpsText() { return cpsText; }
    public static String getFpsText() { return fpsText; }
}
