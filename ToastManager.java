import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Bottom-right toast notifications (success, info, error). */
public final class ToastManager {
    private static volatile JPanel toastLayer;
    private static volatile Window attachedWindow;

    private ToastManager() {
    }

    public static void attach(Window window) {
        if (window == null) {
            return;
        }
        attachedWindow = window;
        SwingUtilities.invokeLater(() -> {
            if (!(window instanceof javax.swing.RootPaneContainer rpc)) {
                return;
            }
            JComponent glass = (JComponent) rpc.getGlassPane();
            glass.setLayout(new GridBagLayout());
            glass.setOpaque(false);
            glass.setVisible(true);

            JPanel layer = new JPanel();
            layer.setLayout(new javax.swing.BoxLayout(layer, javax.swing.BoxLayout.Y_AXIS));
            layer.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1;
            gbc.weighty = 1;
            gbc.anchor = GridBagConstraints.SOUTHEAST;
            gbc.insets = new Insets(0, 0, 24, 24);
            glass.removeAll();
            glass.add(layer, gbc);
            toastLayer = layer;
        });
    }

    public static void showSuccess(String message) {
        show(message, AppUI.SUCCESS, AppUI.SURFACE);
    }

    public static void showInfo(String message) {
        show(message, AppUI.PRIMARY, AppUI.SURFACE);
    }

    public static void showError(String message) {
        show(message, AppUI.DANGER, AppUI.SURFACE);
    }

    public static void show(String message, Color accent, Color bg) {
        Window w = attachedWindow;
        if (w == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JPanel layer = toastLayer;
            if (layer == null) {
                return;
            }
            JPanel toast = buildToast(message, accent, bg);
            layer.add(toast);
            layer.revalidate();
            layer.repaint();
            Timer dismiss = new Timer(3200, e -> {
                layer.remove(toast);
                layer.revalidate();
                layer.repaint();
            });
            dismiss.setRepeats(false);
            dismiss.start();
        });
    }

    private static JPanel buildToast(String message, Color accent, Color bg) {
        JPanel toast = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppUI.RADIUS_MD, AppUI.RADIUS_MD);
                g2.dispose();
            }
        };
        toast.setOpaque(false);
        toast.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        toast.setMaximumSize(new Dimension(360, 80));

        JPanel stripe = new JPanel();
        stripe.setBackground(accent);
        stripe.setPreferredSize(new Dimension(4, 36));
        toast.add(stripe, BorderLayout.WEST);

        JLabel label = new JLabel(message);
        label.setFont(AppUI.fontBody(13));
        label.setForeground(AppUI.TEXT);
        toast.add(label, BorderLayout.CENTER);
        return toast;
    }
}
