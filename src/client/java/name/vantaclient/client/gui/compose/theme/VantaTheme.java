package name.vantaclient.client.gui.compose.theme;

/**
 * Immutable design-token snapshot shared by every VantaCompose renderer and component.
 * A snapshot is resolved once per UI frame so preference changes never produce
 * mixed token values halfway through a render pass.
 */
public record VantaTheme(
        Palette palette,
        Spacing spacing,
        Glass glass,
        Motion motion,
        BackdropBlurStrategy blurStrategy
) {
    public VantaTheme {
        if (palette == null || spacing == null || glass == null || motion == null
                || blurStrategy == null) {
            throw new IllegalArgumentException("A Vanta theme requires every token group.");
        }
    }

    public enum BackdropBlurStrategy {
        MINECRAFT_GUI_RENDER_GRAPH
    }

    public record Palette(
            int canvas, int textPrimary, int textSecondary, int textMuted,
            int accent, int border, int borderSubtle
    ) {}

    public record Spacing(
            int xs, int sm, int md, int lg, int xl, int xxl,
            int radiusSmall, int radiusMedium, int radiusLarge,
            int radiusPanel, int controlHeight, int rowHeight
    ) {
        public Spacing {
            if (xs < 0 || sm < xs || md < sm || lg < md || xl < lg || xxl < xl
                    || radiusSmall < 0 || radiusMedium < radiusSmall
                    || radiusLarge < radiusMedium || radiusPanel < radiusLarge
                    || controlHeight <= 0 || rowHeight <= 0) {
                throw new IllegalArgumentException("Vanta spacing tokens must be ordered and non-negative.");
            }
        }
    }

    public record Glass(
            int panelTint, int elevatedTint, int cardTint, int fieldTint, int popoverTint,
            int panelAlpha, int elevatedAlpha, int cardAlpha, int fieldAlpha, int popoverAlpha,
            int edge, int highlight, int shadow, int scrimTop, int scrimBottom
    ) {
        public Glass {
            requireAlpha(panelAlpha);
            requireAlpha(elevatedAlpha);
            requireAlpha(cardAlpha);
            requireAlpha(fieldAlpha);
            requireAlpha(popoverAlpha);
        }

        private static void requireAlpha(int alpha) {
            if (alpha < 0 || alpha > 255) {
                throw new IllegalArgumentException("Glass alpha must be between 0 and 255.");
            }
        }
    }

    public record Motion(float hoverRate, float pressRate, float selectionRate, boolean enabled) {
        public Motion {
            if (!Float.isFinite(hoverRate) || !Float.isFinite(pressRate)
                    || !Float.isFinite(selectionRate)
                    || hoverRate < 0.0f || pressRate < 0.0f || selectionRate < 0.0f) {
                throw new IllegalArgumentException("Motion rates must be finite and non-negative.");
            }
        }
    }
}
