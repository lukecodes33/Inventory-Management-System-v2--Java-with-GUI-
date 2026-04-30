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
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;

/**
 * Central place for Nimbus theme setup, window styling, and shared Swing chrome (borders, button styles).
 * Call {@link #initialize()} once at startup before showing UI.
 */
public final class AppUI {
    private static final Color BACKGROUND = new Color(241, 245, 249);
    private static final Color SURFACE = new Color(255, 255, 255);
    private static final Color BORDER = new Color(203, 213, 225);
    private static final Color TEXT = new Color(30, 41, 59);
    private static final Color PRIMARY = new Color(59, 130, 246);
    private static final Color PRIMARY_TEXT = new Color(18, 18, 18);

    /** Utility holder for shared UI theme constants and helpers. */
    private AppUI() {
    }

    /** Initializes global look-and-feel and component defaults. */
    public static void initialize() {
        installNimbusLookAndFeel();

        UIManager.put("control", BACKGROUND);
        UIManager.put("info", BACKGROUND);
        UIManager.put("nimbusBase", SURFACE);
        UIManager.put("nimbusBlueGrey", SURFACE);
        UIManager.put("nimbusLightBackground", SURFACE);
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("OptionPane.background", BACKGROUND);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.background", SURFACE);
        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("PasswordField.background", SURFACE);
        UIManager.put("PasswordField.foreground", TEXT);
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.selectionBackground", new Color(191, 219, 254));
        UIManager.put("Table.selectionForeground", PRIMARY_TEXT);
        UIManager.put("TextField.font", new Font("SansSerif", Font.PLAIN, 13));
        UIManager.put("PasswordField.font", new Font("SansSerif", Font.PLAIN, 13));
        UIManager.put("Label.font", new Font("SansSerif", Font.PLAIN, 13));
        UIManager.put("Button.font", new Font("SansSerif", Font.BOLD, 13));

        installGlobalWindowStyler();
    }

    /** Applies primary action styling to a button. */
    public static void stylePrimaryButton(JButton button) {
        button.setBackground(PRIMARY);
        button.setForeground(PRIMARY_TEXT);
        button.setFocusPainted(false);
        button.setBorder(newRoundedBorder(8));
    }

    /** Creates a rounded border used throughout the dark UI. */
    public static Border newRoundedBorder(int radius) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(radius, radius + 4, radius, radius + 4)
        );
    }

    /** Applies theme styling recursively to a panel/component tree. */
    public static void applyPanelBackground(JComponent component) {
        styleTree(component);
    }

    /** Applies theme styling to all components within a window. */
    public static void styleWindow(Window window) {
        if (window == null) {
            return;
        }
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
            button.setBackground(SURFACE);
            button.setForeground(TEXT);
        } else if (component instanceof JTextField field) {
            field.setBackground(SURFACE);
            field.setForeground(TEXT);
            field.setCaretColor(TEXT);
            if (!(field instanceof JPasswordField)) {
                field.setBorder(newRoundedBorder(8));
            }
        } else if (component instanceof JPasswordField passwordField) {
            passwordField.setBackground(SURFACE);
            passwordField.setForeground(TEXT);
            passwordField.setCaretColor(TEXT);
            passwordField.setBorder(newRoundedBorder(8));
        } else if (component instanceof JComboBox<?> comboBox) {
            comboBox.setBackground(SURFACE);
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
                    c.setBackground(isSelected ? new Color(191, 219, 254) : SURFACE);
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
            table.setSelectionBackground(new Color(191, 219, 254));
            table.setSelectionForeground(PRIMARY_TEXT);
            table.getTableHeader().setBackground(new Color(226, 232, 240));
            table.getTableHeader().setForeground(TEXT);
        } else if (component instanceof JScrollPane scrollPane) {
            scrollPane.getViewport().setBackground(BACKGROUND);
            scrollPane.setBackground(BACKGROUND);
            scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        } else if (component instanceof JLabel label) {
            if (!Boolean.TRUE.equals(label.getClientProperty("ims.preserveForeground"))) {
                label.setForeground(TEXT);
            }
        } else if (component instanceof JComponent) {
            component.setBackground(BACKGROUND);
            component.setForeground(TEXT);
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
