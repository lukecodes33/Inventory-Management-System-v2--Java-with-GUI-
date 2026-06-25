import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.border.Border;

/** Sidebar nav button border: rounded outline plus optional 3px left accent when selected. */
public final class NavAccentBorder implements Border {
    private final boolean selected;
    private final int radius;

    public NavAccentBorder(boolean selected, int radius) {
        this.selected = selected;
        this.radius = radius;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color line = selected ? AppUI.PRIMARY : AppUI.BORDER_SOFT;
        g2.setColor(line);
        g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        if (selected) {
            g2.setColor(AppUI.PRIMARY);
            g2.fillRoundRect(x, y + 6, 3, height - 12, 2, 2);
        }
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(8, selected ? 14 : 12, 8, 12);
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}
