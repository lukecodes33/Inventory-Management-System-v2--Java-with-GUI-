import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Thin rounded scrollbar thumb matching {@link AppUI} dark surfaces. */
public final class ModernScrollBarUI extends BasicScrollBarUI {
    private static final int THUMB_WIDTH = 7;

    @Override
    protected void configureScrollBarColors() {
        trackColor = AppUI.BACKGROUND;
        thumbColor = AppUI.SURFACE_ELEVATED;
        thumbHighlightColor = AppUI.BORDER;
        thumbDarkShadowColor = AppUI.BORDER;
        thumbLightShadowColor = AppUI.BORDER;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return zeroSizeButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return zeroSizeButton();
    }

    private static JButton zeroSizeButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        return b;
    }

    @Override
    protected Dimension getMinimumThumbSize() {
        return new Dimension(THUMB_WIDTH, 32);
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        g.setColor(trackColor);
        g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color base = isThumbRollover() ? brighten(thumbColor, 18) : thumbColor;
        g2.setColor(base);
        int arc = THUMB_WIDTH;
        if (scrollbar.getOrientation() == VERTICAL) {
            int x = thumbBounds.x + (thumbBounds.width - THUMB_WIDTH) / 2;
            g2.fillRoundRect(x, thumbBounds.y, THUMB_WIDTH, thumbBounds.height, arc, arc);
        } else {
            int y = thumbBounds.y + (thumbBounds.height - THUMB_WIDTH) / 2;
            g2.fillRoundRect(thumbBounds.x, y, thumbBounds.width, THUMB_WIDTH, arc, arc);
        }
        g2.dispose();
    }

    private static Color brighten(Color c, int delta) {
        return new Color(
                Math.min(255, c.getRed() + delta),
                Math.min(255, c.getGreen() + delta),
                Math.min(255, c.getBlue() + delta),
                c.getAlpha());
    }
}
