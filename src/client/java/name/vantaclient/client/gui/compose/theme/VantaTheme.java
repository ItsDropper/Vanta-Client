package name.vantaclient.client.gui.compose.theme;

/**
 * Immutable design-token snapshot shared by every Vanta renderer and component.
 * A snapshot is resolved once per UI frame so preference changes never produce
 * mixed token values halfway through a render pass.
 */
public record VantaTheme(
        Palette palette,
        Spacing spacing,
        Typography typography,
        Glass glass,
        Motion motion,
        BackdropBlurStrategy blurStrategy
) {
    public VantaTheme {
        if (palette == null || spacing == null || typography == null || glass == null
                || motion == null || blurStrategy == null) {
            throw new IllegalArgumentException("A Vanta theme requires every token group.");
        }
    }

    public enum BackdropBlurStrategy {
        MINECRAFT_GUI_RENDER_GRAPH
    }

    /** Semantic colors; components must use roles rather than inventing local shades. */
    public record Palette(
            int canvas, int textPrimary, int textSecondary, int textMuted,
            int accent, int accentForeground, int border, int borderSubtle,
            int focus, int selection, int info, int success, int warning, int danger
    ) {}

    /** Ordered layout and shape scale, expressed in Minecraft GUI logical pixels. */
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

    /** Named text hierarchy and line metrics shared by retained text primitives. */
    public record Typography(
            int displaySize, int headingSize, int titleSize, int bodySize,
            int labelSize, int captionSize, int titleLineHeight,
            int bodyLineHeight, int captionLineHeight
    ) {
        public Typography {
            if (displaySize < headingSize || headingSize < titleSize
                    || titleSize < bodySize || bodySize < labelSize
                    || labelSize < captionSize || captionSize <= 0
                    || titleLineHeight < titleSize || bodyLineHeight < bodySize
                    || captionLineHeight < captionSize) {
                throw new IllegalArgumentException("Vanta typography tokens must form a valid type scale.");
            }
        }
    }

    /** Material tints and state overlays used by the Minecraft-native glass renderer. */
    public record Glass(
            int panelTint, int elevatedTint, int cardTint, int fieldTint, int popoverTint,
            int panelAlpha, int elevatedAlpha, int cardAlpha, int fieldAlpha, int popoverAlpha,
            int edge, int highlight, int shadow, int scrimTop, int scrimBottom,
            int shadowAlpha, int highlightAlpha, int borderAlpha,
            int hoverAlpha, int selectedAlpha, int focusAlpha, int disabledAlpha
    ) {
        public Glass {
            requireAlpha(panelAlpha);
            requireAlpha(elevatedAlpha);
            requireAlpha(cardAlpha);
            requireAlpha(fieldAlpha);
            requireAlpha(popoverAlpha);
            requireAlpha(shadowAlpha);
            requireAlpha(highlightAlpha);
            requireAlpha(borderAlpha);
            requireAlpha(hoverAlpha);
            requireAlpha(selectedAlpha);
            requireAlpha(focusAlpha);
            requireAlpha(disabledAlpha);
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
