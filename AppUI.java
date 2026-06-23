import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JRootPane;
import javax.swing.JPanel;
import javax.swing.RootPaneContainer;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.util.Locale;

/**
 * Central place for Nimbus theme setup, window styling, and shared Swing chrome (borders, button styles).
 * Call {@link #initialize()} once at startup before showing UI.
 */
public final class AppUI {
    /** Page canvas — oklch(14.5% 0 0). */
    public static final Color BACKGROUND = new Color(0x0a0a0a);
    /** Card / panel surface — oklch(20.5% 0 0). */
    public static final Color SURFACE = new Color(0x171717);
    /** Raised controls, table headers, zebra stripes — oklch(26.9% 0 0). */
    public static final Color SURFACE_ELEVATED = new Color(0x262626);
    /** Dividers and outlines — oklch(27.5% 0 0). */
    public static final Color BORDER = new Color(0x282828);
    /** Text fields and inputs — oklch(32.5% 0 0). */
    public static final Color INPUT = new Color(0x343434);
    /** Primary copy — oklch(98.5% 0 0). */
    public static final Color TEXT = new Color(0xfafafa);
    /** Secondary copy — oklch(70.8% 0 0). */
    public static final Color TEXT_MUTED = new Color(0xa1a1a1);
    /** Teal call-to-action — oklch(67.5% 0.0965 187). */
    public static final Color PRIMARY = new Color(0x41aaa1);
    /** Text on teal buttons. */
    public static final Color PRIMARY_TEXT = new Color(0x0a0a0a);
    /** Portfolio accent / active nav — oklch(48.8% 0.243 264). */
    public static final Color ACCENT = new Color(0x1447e6);
    /** List and table selection fill. */
    public static final Color SELECTION = new Color(0x1e3d3a);
    /** Positive P/L and gains (readable on dark). */
    public static final Color SUCCESS = new Color(0x4ade80);
    /** Negative P/L and losses (readable on dark). */
    public static final Color DANGER = new Color(0xf87171);

    /** {@link JLabel#getClientProperty(Object)} — skip automatic foreground theming. */
    public static final String CLIENT_PRESERVE_FOREGROUND = "ims.preserveForeground";
    /** {@link JComponent#getClientProperty(Object)} value {@code "card"} or {@code "elevated"}. */
    public static final String CLIENT_SURFACE = "ims.surface";

    /** Utility holder for shared UI theme constants and helpers. */
    private AppUI() {
    }

    /** Initializes global look-and-feel and component defaults (call once before any Swing UI). */
    public static void initialize() {
        installPlatformDarkChrome();
        installNimbusLookAndFeel();

        UIManager.put("control", BACKGROUND);
        UIManager.put("info", BACKGROUND);
        UIManager.put("nimbusBase", SURFACE);
        UIManager.put("nimbusBlueGrey", SURFACE_ELEVATED);
        UIManager.put("nimbusLightBackground", SURFACE);
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.background", SURFACE_ELEVATED);
        UIManager.put("TextField.background", INPUT);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("PasswordField.background", INPUT);
        UIManager.put("PasswordField.foreground", TEXT);
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.selectionBackground", SELECTION);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TextField.font", new Font("SansSerif", Font.PLAIN, 13));
        UIManager.put("PasswordField.font", new Font("SansSerif", Font.PLAIN, 13));
        UIManager.put("Label.font", new Font("SansSerif", Font.PLAIN, 13));
        UIManager.put("Button.font", new Font("SansSerif", Font.BOLD, 13));
        UIManager.put("SplitPane.background", BACKGROUND);
        UIManager.put("SplitPane.dividerFocusColor", BORDER);

        installGlobalWindowStyler();
    }

    /**
     * Requests a dark native window chrome where the OS supports it (macOS menu bar and title bar).
     * Safe to call before {@link #initialize()}; also runs automatically during initialize.
     */
    public static void installPlatformDarkChrome() {
        if (isMacOs()) {
            System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");
        }
    }

    /**
     * On macOS, replaces the bright native title strip with an in-app dark bar matching {@link #SURFACE}.
     *
     * @return {@code true} when windows should include {@link #createApplicationTitleBar(String)}
     */
    public static boolean usesEmbeddedTitleBar() {
        return isMacOs();
    }

    /**
     * Dark title strip for the main window and dialogs (macOS embedded title bar workflow).
     *
     * @param title window caption (e.g. application name)
     * @return north bar panel
     */
    public static JPanel createApplicationTitleBar(String title) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(true);
        bar.setBackground(SURFACE);
        int top = isMacOs() ? 12 : 8;
        int left = isMacOs() ? 78 : 18;
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(top, left, 8, 18)));
        JLabel label = new JLabel(title, SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.setForeground(TEXT);
        bar.add(label, BorderLayout.CENTER);
        return bar;
    }

    /** Paints window / root-pane backgrounds and enables macOS unified dark title bar integration. */
    public static void applyWindowChrome(Window window) {
        if (window == null) {
            return;
        }
        window.setBackground(BACKGROUND);
        if (window instanceof RootPaneContainer rpc) {
            JRootPane root = rpc.getRootPane();
            root.setBackground(BACKGROUND);
            if (isMacOs()) {
                root.putClientProperty("apple.awt.fullWindowContent", true);
                root.putClientProperty("apple.awt.transparentTitleBar", true);
                root.putClientProperty("apple.awt.windowTitleVisible", false);
            }
        }
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    /** @param button primary-colored action control */
    public static void stylePrimaryButton(JButton button) {
        button.setBackground(PRIMARY);
        button.setForeground(PRIMARY_TEXT);
        button.setFocusPainted(false);
        button.setBorder(newRoundedBorder(8));
        button.setOpaque(true);
    }

    /** @param button secondary control on dark surfaces */
    public static void styleSecondaryButton(JButton button) {
        button.setBackground(SURFACE_ELEVATED);
        button.setForeground(TEXT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        button.setOpaque(true);
    }

    /** Marks a panel as a card surface ({@link #SURFACE}) for tree styling. */
    public static void markCardSurface(JComponent component) {
        component.putClientProperty(CLIENT_SURFACE, "card");
        component.setOpaque(true);
        component.setBackground(SURFACE);
    }

    /** Marks a panel as a raised inner block ({@link #SURFACE_ELEVATED}). */
    public static void markElevatedSurface(JComponent component) {
        component.putClientProperty(CLIENT_SURFACE, "elevated");
        component.setOpaque(true);
        component.setBackground(SURFACE_ELEVATED);
    }

    /** @param radius corner padding mirrored for empty insets inside the line border */
    public static Border newRoundedBorder(int radius) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(radius, radius + 4, radius, radius + 4)
        );
    }

    /** @param component root Swing node whose subtree should inherit background/text defaults */
    public static void applyPanelBackground(JComponent component) {
        styleTree(component);
    }

    /** Applies {@link #styleTree(Component)} depth-first beginning at each immediate child of {@code window}. */
    public static void styleWindow(Window window) {
        if (window == null) {
            return;
        }
        applyWindowChrome(window);
        for (Component component : window.getComponents()) {
            styleTree(component);
        }
    }

    /** Recursively styles component tree descendants. */
    private static void styleTree(Component component) {
        styleComponent(component);
        if (component instanceof JComponent jc) {
            for (Component child : jc.getComponents()) {
                styleTree(child);
            }
        }
    }

    /** Applies style rules for supported Swing component types. */
    private static void styleComponent(Component component) {
        if (component instanceof JButton button) {
            if (!PRIMARY.equals(button.getBackground())) {
                button.setBackground(SURFACE_ELEVATED);
                button.setForeground(TEXT);
            }
        } else if (component instanceof JTextField field) {
            field.setBackground(INPUT);
            field.setForeground(TEXT);
            field.setCaretColor(TEXT);
            if (!(field instanceof JPasswordField)) {
                field.setBorder(newRoundedBorder(8));
            }
        } else if (component instanceof JPasswordField passwordField) {
            passwordField.setBackground(INPUT);
            passwordField.setForeground(TEXT);
            passwordField.setCaretColor(TEXT);
            passwordField.setBorder(newRoundedBorder(8));
        } else if (component instanceof JComboBox<?> comboBox) {
            comboBox.setBackground(INPUT);
            comboBox.setForeground(TEXT);
            comboBox.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(
                        JList<?> list,
                        Object value,
                        int index,
                        boolean isSelected,
                        boolean cellHasFocus
                ) {
                    Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    c.setForeground(TEXT);
                    c.setBackground(isSelected ? SELECTION : INPUT);
                    return c;
                }
            });
        } else if (component instanceof JList<?> list) {
            list.setBackground(SURFACE);
            list.setForeground(TEXT);
        } else if (component instanceof JTable table) {
            table.setBackground(SURFACE);
            table.setForeground(TEXT);
            table.setGridColor(BORDER);
            table.setSelectionBackground(SELECTION);
            table.setSelectionForeground(TEXT);
            table.getTableHeader().setBackground(SURFACE_ELEVATED);
            table.getTableHeader().setForeground(TEXT);
        } else if (component instanceof JScrollPane scrollPane) {
            scrollPane.getViewport().setBackground(BACKGROUND);
            scrollPane.setBackground(BACKGROUND);
            scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        } else if (component instanceof JLabel label) {
            if (!Boolean.TRUE.equals(label.getClientProperty(CLIENT_PRESERVE_FOREGROUND))) {
                label.setForeground(TEXT);
            }
        } else if (component instanceof JComponent jc) {
            Object surface = jc.getClientProperty(CLIENT_SURFACE);
            if ("card".equals(surface)) {
                jc.setBackground(SURFACE);
            } else if ("elevated".equals(surface)) {
                jc.setBackground(SURFACE_ELEVATED);
            } else {
                jc.setBackground(BACKGROUND);
            }
            jc.setForeground(TEXT);
        } else {
            component.setBackground(BACKGROUND);
            component.setForeground(TEXT);
        }
    }

    /** Installs Nimbus look-and-feel when available. */
    private static void installNimbusLookAndFeel() {
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                         UnsupportedLookAndFeelException ignored) {
                    // Fallback to default look and feel.
                }
                break;
            }
        }
    }

    /** Installs global window-open listener to auto-style new windows. */
    private static void installGlobalWindowStyler() {
        AWTEventListener listener = event -> {
            if (event instanceof WindowEvent windowEvent
                    && windowEvent.getID() == WindowEvent.WINDOW_OPENED) {
                styleWindow(windowEvent.getWindow());
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK);
    }
}
