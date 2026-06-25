import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.border.Border;

/** Rounded rectangle outline with optional fill (used for cards, inputs, buttons). */
public final class RoundedBorder implements Border {
    private final int radius;
    private final Color lineColor;
    private final int lineWidth;
    private final Color fillColor;
    private final Insets padding;

    public RoundedBorder(int radius, Color lineColor, int lineWidth, Color fillColor, Insets padding) {
        this.radius = Math.max(0, radius);
        this.lineColor = lineColor;
        this.lineWidth = Math.max(1, lineWidth);
        this.fillColor = fillColor;
        this.padding = padding == null ? new Insets(0, 0, 0, 0) : padding;
    }

    public static RoundedBorder outline(int radius, Color lineColor) {
        return new RoundedBorder(radius, lineColor, 1, null, new Insets(0, 0, 0, 0));
    }

    public static RoundedBorder filled(int radius, Color fill, Color lineColor) {
        return new RoundedBorder(radius, lineColor, 1, fill, new Insets(0, 0, 0, 0));
    }

    public RoundedBorder withPadding(int top, int left, int bottom, int right) {
        return new RoundedBorder(radius, lineColor, lineWidth, fillColor, new Insets(top, left, bottom, right));
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int inset = lineWidth;
        int w = width - inset * 2;
        int h = height - inset * 2;
        if (fillColor != null) {
            g2.setColor(fillColor);
            g2.fillRoundRect(x + inset, y + inset, w, h, radius, radius);
        }
        if (lineColor != null) {
            g2.setColor(lineColor);
            g2.setStroke(new java.awt.BasicStroke(lineWidth));
            g2.drawRoundRect(x + inset, y + inset, w, h, radius, radius);
        }
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        int p = lineWidth + Math.max(padding.top, Math.max(padding.left, Math.max(padding.bottom, padding.right)));
        return new Insets(padding.top + lineWidth, padding.left + lineWidth,
                padding.bottom + lineWidth, padding.right + lineWidth);
    }

    @Override
    public boolean isBorderOpaque() {
        return fillColor != null;
    }
}
