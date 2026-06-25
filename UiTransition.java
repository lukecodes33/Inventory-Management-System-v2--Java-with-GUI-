import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Short fade-in when swapping workspace cards. */
public final class UiTransition {
    private static final int DURATION_MS = 150;
    private static final int TICK_MS = 16;

    private UiTransition() {
    }

    /** Wraps {@code content} and fades it in over ~150ms. */
    public static JPanel wrapFadeIn(JPanel content) {
        JPanel host = new JPanel(new BorderLayout()) {
            private float alpha = 0f;
            private Timer timer;

            {
                setOpaque(false);
                timer = new Timer(TICK_MS, this::tick);
                timer.start();
            }

            private void tick(ActionEvent e) {
                alpha += (float) TICK_MS / DURATION_MS;
                if (alpha >= 1f) {
                    alpha = 1f;
                    timer.stop();
                }
                repaint();
            }

            @Override
            protected void paintChildren(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                super.paintChildren(g2);
                g2.dispose();
            }
        };
        host.add(content, BorderLayout.CENTER);
        return host;
    }
}
