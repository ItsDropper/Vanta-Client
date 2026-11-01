package name.vantaclient.client.gui.components;

import name.vantaclient.client.feature.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public class CategoryButton extends AbstractWidget {

    private final Category category;
    private final Runnable action;
    private final BooleanSupplier selected;

    public CategoryButton(
            int x,
            int y,
            Category category,
            Runnable action,
            BooleanSupplier selected
    ) {
        super(
                x,
                y,
                72,
                22,
                Component.literal(category.name())
        );

        this.category = category;
        this.action = action;
        this.selected = selected;
    }

    @Override
    public void renderWidget(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        boolean hovered = isHovered();
        boolean isSelected = selected.getAsBoolean();

        /*
         * Background.
         */
        int background;

        if (isSelected) {
            background = 0xFF292932;
        } else if (hovered) {
            background = 0xFF25252C;
        } else {
            background = 0xFF1D1D21;
        }

        graphics.fill(
                getX(),
                getY(),
                getX() + width,
                getY() + height,
                background
        );

        /*
         * Selected indicator.
         */
        if (isSelected) {
            graphics.fill(
                    getX(),
                    getY(),
                    getX() + 2,
                    getY() + height,
                    0xFF6677FF
            );
        }

        /*
         * Bottom accent when selected.
         */
        if (isSelected) {
            graphics.fill(
                    getX() + 2,
                    getY() + height - 1,
                    getX() + width,
                    getY() + height,
                    0xFF6677FF
            );
        }

        /*
         * Category name.
         */
        graphics.drawString(
                minecraft.font,
                category.name(),
                getX() + 8,
                getY() + 6,
                isSelected
                        ? 0xFFFFFFFF
                        : hovered
                          ? 0xFFE0E0E0
                          : 0xFF9A9A9F
        );
    }

    @Override
    public boolean mouseClicked(
            MouseButtonEvent event,
            boolean bl
    ) {
        if (!isActive()) {
            return false;
        }

        if (!isMouseOver(event.x(), event.y())) {
            return false;
        }

        playDownSound(
                Minecraft.getInstance()
                        .getSoundManager()
        );

        action.run();

        return true;
    }

    @Override
    protected void updateWidgetNarration(
            NarrationElementOutput narration
    ) {
        narration.add(
                NarratedElementType.TITLE,
                Component.literal(
                        "Category " + category.name()
                )
        );
    }
}