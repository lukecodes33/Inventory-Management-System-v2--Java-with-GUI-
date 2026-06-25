import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPasswordField;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JRootPane;
import javax.swing.JPanel;
import javax.swing.RootPaneContainer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.Painter;
import javax.swing.plaf.ColorUIResource;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Central place for Nimbus theme setup, window styling, and shared Swing chrome (borders, button styles).
 * Call {@link #initialize()} once at startup before showing UI.
 */
public final class AppUI {
    public static final int RADIUS_SM = 6;
    public static final int RADIUS_MD = 10;
    public static final int RADIUS_LG = 14;

    /** Page canvas — oklch(14.5% 0 0). */
    public static final Color BACKGROUND = new Color(0x0a0a0a);
    /** Card / panel surface — oklch(20.5% 0 0). */
    public static final Color SURFACE = new Color(0x171717);
    /** Raised controls, table headers, zebra stripes — oklch(26.9% 0 0). */
    public static final Color SURFACE_ELEVATED = new Color(0x262626);
    /** Dividers and outlines — oklch(27.5% 0 0). */
    public static final Color BORDER = new Color(0x282828);
    /** Softer card outline (less harsh than {@link #BORDER}). */
    public static final Color BORDER_SOFT = new Color(0x333333);
    /** Text fields and inputs — oklch(32.5% 0 0). */
    public static final Color INPUT = new Color(0x343434);
    /** Primary copy — oklch(98.5% 0 0). */
    public static final Color TEXT = new Color(0xfafafa);
    /** Secondary copy — oklch(70.8% 0 0). */
    public static final Color TEXT_MUTED = new Color(0xa1a1a1);
    /** Teal call-to-action — oklch(67.5% 0.0965 187). */
    public static final Color PRIMARY = new Color(0x41aaa1);
    /** Unified accent (same as primary). */
    public static final Color ACCENT = PRIMARY;
    /** Text on teal buttons. */
    public static final Color PRIMARY_TEXT = new Color(0x0a0a0a);
    /** List and table selection fill. */
    public static final Color SELECTION = new Color(0x1e3d3a);
    /** Focus ring around inputs and buttons. */
    public static final Color FOCUS_RING = new Color(0x6641aaa1, true);
    /** Positive P/L and gains (readable on dark). */
    public static final Color SUCCESS = new Color(0x4ade80);
    /** Negative P/L and losses (readable on dark). */
    public static final Color DANGER = new Color(0xf87171);
    /** Warning / amber chips. */
    public static final Color WARNING = new Color(0xfbbf24);
    /** Info chip background. */
    public static final Color INFO_BG = new Color(0x1e293b);

    public static final int CONTROL_HEIGHT = 36;
    public static final int INPUT_MIN_WIDTH = 180;
    public static final int TABLE_ROW_HEIGHT = 38;

    public static final String CLIENT_PRESERVE_FOREGROUND = "ims.preserveForeground";
    public static final String CLIENT_SURFACE = "ims.surface";
    public static final String CLIENT_BUTTON_BASE = "ims.button.base";
    public static final String CLIENT_BUTTON_HOVER = "ims.button.hover";
    public static final String CLIENT_BUTTON_PRESSED = "ims.button.pressed";
    public static final String CLIENT_BUTTON_FG = "ims.button.fg";
    public static final String CLIENT_FIELD_ERROR = "ims.field.error";

    private static Font fontRegular;
    private static Font fontSemiBold;
    private static Font fontBold;

    private AppUI() {
    }

    public static void initialize() {
        loadFonts();
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
        UIManager.put("TextField.font", fontBody(14));
        UIManager.put("PasswordField.font", fontBody(14));
        UIManager.put("Label.font", fontBody(14));
        UIManager.put("Button.font", fontBody(14));
        UIManager.put("ComboBox.font", fontBody(14));
        UIManager.put("Table.font", fontBody(14));
        UIManager.put("TableHeader.font", fontSemiBold(13));
        UIManager.put("SplitPane.background", BACKGROUND);
        UIManager.put("SplitPane.dividerFocusColor", BORDER);
        UIManager.put("ScrollBar.thumb", SURFACE_ELEVATED);
        UIManager.put("ScrollBar.track", BACKGROUND);

        installNimbusComboOverrides();
        installGlobalWindowStyler();
    }

    private static void loadFonts() {
        fontRegular = loadFontFile("resources/fonts/Inter-Regular.ttf", Font.PLAIN);
        fontSemiBold = loadFontFile("resources/fonts/Inter-SemiBold.ttf", Font.BOLD);
        fontBold = loadFontFile("resources/fonts/Inter-Bold.ttf", Font.BOLD);
        if (fontRegular == null) {
            fontRegular = new Font("Segoe UI", Font.PLAIN, 14);
            fontSemiBold = fontRegular.deriveFont(Font.BOLD);
            fontBold = fontRegular.deriveFont(Font.BOLD);
        }
    }

    private static Font loadFontFile(String path, int fallbackStyle) {
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(path);
            InputStream in = java.nio.file.Files.exists(filePath)
                    ? java.nio.file.Files.newInputStream(filePath)
                    : AppUI.class.getResourceAsStream("/" + path);
            if (in == null) {
                return null;
            }
            try (InputStream stream = in) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, stream);
                return f.deriveFont(14f);
            }
        } catch (Exception ex) {
            return null;
        }
    }

    public static Font fontBody(float size) {
        return fontRegular.deriveFont(size);
    }

    public static Font fontCaption(float size) {
        return fontRegular.deriveFont(size);
    }

    public static Font fontSemiBold(float size) {
        return fontSemiBold.deriveFont(size);
    }

    public static Font fontTitle(float size) {
        return fontSemiBold.deriveFont(size);
    }

    public static Font fontPageTitle(float size) {
        return fontBold.deriveFont(size);
    }

    public static Font fontTabular(float size) {
        return fontRegular.deriveFont(size);
    }

    private static void installNimbusComboOverrides() {
        Painter<JComponent> fieldFill = (g, c, w, h) -> {
            g.setColor(INPUT);
            g.fillRoundRect(0, 0, w - 1, h - 1, RADIUS_SM, RADIUS_SM);
            g.setColor(BORDER_SOFT);
            g.drawRoundRect(0, 0, w - 1, h - 1, RADIUS_SM, RADIUS_SM);
        };
        Painter<JComponent> disabledFill = (g, c, w, h) -> {
            g.setColor(SURFACE_ELEVATED);
            g.fillRoundRect(0, 0, w - 1, h - 1, RADIUS_SM, RADIUS_SM);
        };
        Painter<JComponent> arrowFill = (g, c, w, h) -> {
            g.setColor(INPUT);
            g.fillRect(0, 0, w, h);
        };
        Painter<JComponent> arrowGlyph = (g, c, w, h) -> {
            int cx = w / 2;
            int cy = h / 2;
            g.setColor(TEXT_MUTED);
            int[] xs = {cx - 4, cx + 4, cx};
            int[] ys = {cy - 2, cy - 2, cy + 3};
            g.fillPolygon(xs, ys, 3);
        };

        String[] enabledStates = {
                "Enabled", "MouseOver", "Pressed", "Focused", "MouseOver",
                "Enabled+Selected", "Focused+Pressed", "Focused+MouseOver",
                "Editable+Enabled", "Editable+Focused", "Editable+Pressed", "Editable+MouseOver"
        };
        for (String s : enabledStates) {
            UIManager.put("ComboBox[" + s + "].backgroundPainter", fieldFill);
        }
        UIManager.put("ComboBox[Disabled].backgroundPainter", disabledFill);
        UIManager.put("ComboBox[Disabled+Editable].backgroundPainter", disabledFill);

        String[] arrowStates = {"Enabled", "Pressed", "MouseOver", "Selected", "Enabled+Editable"};
        for (String s : arrowStates) {
            UIManager.put("ComboBox:\"ComboBox.arrowButton\"[" + s + "].backgroundPainter", arrowFill);
            UIManager.put("ComboBox:\"ComboBox.arrowButton\"[" + s + "].foregroundPainter", arrowGlyph);
        }
        UIManager.put("ComboBox:\"ComboBox.arrowButton\"[Disabled].backgroundPainter", arrowFill);

        UIManager.put("ComboBox.background", new ColorUIResource(INPUT));
        UIManager.put("ComboBox.foreground", new ColorUIResource(TEXT));
        UIManager.put("ComboBox:\"ComboBox.listRenderer\".background", new ColorUIResource(INPUT));
        UIManager.put("ComboBox:\"ComboBox.listRenderer\".foreground", new ColorUIResource(TEXT));
    }

    public static void installPlatformDarkChrome() {
        if (isMacOs()) {
            System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");
        }
    }

    public static boolean usesEmbeddedTitleBar() {
        return isMacOs();
    }

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
        label.setFont(fontSemiBold(14f));
        label.setForeground(TEXT);
        bar.add(label, BorderLayout.CENTER);
        return bar;
    }

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
        ToastManager.attach(window);
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    public static void stylePrimaryButton(JButton button) {
        installButtonInteraction(button, PRIMARY, brighten(PRIMARY, 22), darken(PRIMARY, 18), PRIMARY_TEXT);
        button.setMinimumSize(new Dimension(96, CONTROL_HEIGHT));
    }

    public static void styleSecondaryButton(JButton button) {
        installButtonInteraction(button, SURFACE_ELEVATED, brighten(SURFACE_ELEVATED, 16),
                darken(SURFACE_ELEVATED, 12), TEXT);
        button.setMinimumSize(new Dimension(96, CONTROL_HEIGHT));
    }

    public static void styleNavButton(JButton button, boolean selected) {
        Color base = selected ? SURFACE_ELEVATED : SURFACE;
        Color hover = brighten(base, 12);
        Color pressed = darken(base, 8);
        installButtonInteraction(button, base, hover, pressed, selected ? PRIMARY : TEXT);
        button.setBorder(new NavAccentBorder(selected, RADIUS_SM));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(fontBody(13));
        button.setMinimumSize(new Dimension(120, CONTROL_HEIGHT));
    }

    private static void installButtonInteraction(
            JButton button, Color base, Color hover, Color pressed, Color fg
    ) {
        button.putClientProperty(CLIENT_BUTTON_BASE, base);
        button.putClientProperty(CLIENT_BUTTON_HOVER, hover);
        button.putClientProperty(CLIENT_BUTTON_PRESSED, pressed);
        button.putClientProperty(CLIENT_BUTTON_FG, fg);
        button.setBackground(base);
        button.setForeground(fg);
        button.setFocusPainted(false);
        // Outline only — a filled border is painted after button text and would hide the label.
        button.setBorder(new RoundedBorder(RADIUS_SM, BORDER_SOFT, 1, null, new java.awt.Insets(8, 14, 8, 14)));
        button.setOpaque(true);

        if (Boolean.TRUE.equals(button.getClientProperty("ims.button.interaction"))) {
            return;
        }
        button.putClientProperty("ims.button.interaction", Boolean.TRUE);

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(hover);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(base);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(pressed);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(button.getMousePosition() != null ? hover : base);
                }
            }
        });

        button.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                button.setBorder(new RoundedBorder(RADIUS_SM, PRIMARY, 2, null, new java.awt.Insets(7, 13, 7, 13)));
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (button.getBorder() instanceof NavAccentBorder navBorder) {
                    button.setBorder(new NavAccentBorder(navBorder.isSelected(), RADIUS_SM));
                } else {
                    button.setBorder(new RoundedBorder(RADIUS_SM, BORDER_SOFT, 1, null, new java.awt.Insets(8, 14, 8, 14)));
                }
            }
        });
    }

    public static void markCardSurface(JComponent component) {
        component.putClientProperty(CLIENT_SURFACE, "card");
        component.setOpaque(true);
        component.setBackground(SURFACE);
        component.setBorder(cardBorder());
    }

    public static Border cardBorder() {
        return RoundedBorder.filled(RADIUS_MD, SURFACE, BORDER_SOFT);
    }

    public static Border lineBorder() {
        return RoundedBorder.outline(RADIUS_SM, BORDER_SOFT);
    }

    public static Border inputBorder() {
        return new CompoundBorder(
                RoundedBorder.outline(RADIUS_SM, BORDER_SOFT),
                BorderFactory.createEmptyBorder(5, 10, 5, 10));
    }

    public static Border controlBorder(Color borderColor) {
        Color line = borderColor == null ? BORDER_SOFT : borderColor;
        return new CompoundBorder(
                RoundedBorder.outline(RADIUS_SM, line),
                BorderFactory.createEmptyBorder(8, 14, 8, 14));
    }

    public static Border newRoundedBorder(int radius) {
        return RoundedBorder.outline(radius, BORDER_SOFT);
    }

    public static void applyInputField(JTextField field) {
        if (field == null) {
            return;
        }
        field.setBorder(inputBorder());
        field.setFont(fontBody(14));
        Dimension pref = field.getPreferredSize();
        int w = Math.max(pref.width, INPUT_MIN_WIDTH);
        field.setPreferredSize(new Dimension(w, CONTROL_HEIGHT));
        field.setMinimumSize(new Dimension(Math.min(w, INPUT_MIN_WIDTH), CONTROL_HEIGHT));
        installInputFocusRing(field);
    }

    public static void applyPasswordField(JPasswordField field) {
        if (field == null) {
            return;
        }
        field.setBorder(inputBorder());
        field.setFont(fontBody(14));
        Dimension pref = field.getPreferredSize();
        int w = Math.max(pref.width, INPUT_MIN_WIDTH);
        field.setPreferredSize(new Dimension(w, CONTROL_HEIGHT));
        field.setMinimumSize(new Dimension(Math.min(w, INPUT_MIN_WIDTH), CONTROL_HEIGHT));
        installInputFocusRing(field);
    }

    public static void applyComboField(JComboBox<?> combo) {
        if (combo == null) {
            return;
        }
        combo.setBorder(inputBorder());
        combo.setFont(fontBody(14));
        Dimension pref = combo.getPreferredSize();
        int w = Math.max(pref.width, INPUT_MIN_WIDTH);
        combo.setPreferredSize(new Dimension(w, CONTROL_HEIGHT));
        combo.setMinimumSize(new Dimension(Math.min(w, INPUT_MIN_WIDTH), CONTROL_HEIGHT));
        installInputFocusRing(combo);
    }

    private static void installInputFocusRing(JComponent field) {
        if (Boolean.TRUE.equals(field.getClientProperty("ims.input.focus"))) {
            return;
        }
        field.putClientProperty("ims.input.focus", Boolean.TRUE);
        Border normal = field.getBorder();
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        RoundedBorder.outline(RADIUS_SM, PRIMARY),
                        BorderFactory.createEmptyBorder(5, 10, 5, 10)));
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (!Boolean.TRUE.equals(field.getClientProperty(CLIENT_FIELD_ERROR))) {
                    field.setBorder(normal);
                }
            }
        });
    }

    public static void setPlaceholder(JTextField field, String placeholder) {
        if (field == null) {
            return;
        }
        field.putClientProperty("JTextField.placeholderText", placeholder);
        if (field.getText().isEmpty()) {
            field.setToolTipText(placeholder);
        }
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void refresh() {
                field.setToolTipText(field.getText().isEmpty() ? placeholder : null);
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                refresh();
            }
        });
    }

    public static JLabel attachFieldValidator(JTextField field, Predicate<String> valid, String errorMessage) {
        JLabel error = new JLabel(" ");
        error.setFont(fontCaption(11));
        error.setForeground(DANGER);
        error.putClientProperty(CLIENT_PRESERVE_FOREGROUND, Boolean.TRUE);
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String text = field.getText();
                if (text == null || text.isBlank() || valid.test(text.trim())) {
                    field.putClientProperty(CLIENT_FIELD_ERROR, null);
                    field.setBorder(inputBorder());
                    error.setText(" ");
                } else {
                    field.putClientProperty(CLIENT_FIELD_ERROR, Boolean.TRUE);
                    field.setBorder(BorderFactory.createCompoundBorder(
                            RoundedBorder.outline(RADIUS_SM, DANGER),
                            BorderFactory.createEmptyBorder(5, 10, 5, 10)));
                    error.setText(errorMessage);
                }
            }
        });
        return error;
    }

    public static JPanel buildStackedField(String labelText, JComponent field) {
        JPanel row = new JPanel();
        row.setLayout(new javax.swing.BoxLayout(row, javax.swing.BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(labelText);
        label.setFont(fontCaption(12));
        label.setForeground(TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(label);
        row.add(javax.swing.Box.createVerticalStrut(4));
        row.add(field);
        return row;
    }

    public static void polishTable(JTable table) {
        if (table == null) {
            return;
        }
        table.setRowHeight(TABLE_ROW_HEIGHT);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(BORDER_SOFT);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(SELECTION);
        table.setSelectionForeground(TEXT);
        table.setFont(fontBody(13));
        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.setBackground(SURFACE_ELEVATED);
            header.setForeground(TEXT);
            header.setFont(fontSemiBold(12));
            header.setPreferredSize(new Dimension(header.getPreferredSize().width, TABLE_ROW_HEIGHT + 4));
            header.setReorderingAllowed(false);
        }
        installTableZebra(table);
        installTableHoverHighlight(table);
        installMoneyColumnRenderers(table);
    }

    private static void installTableZebra(JTable table) {
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column
            ) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? SURFACE : SURFACE_ELEVATED);
                }
                c.setForeground(TEXT);
                setFont(fontBody(13));
                return c;
            }
        });
    }

    private static void installTableHoverHighlight(JTable table) {
        final int[] hoverRow = {-1};
        table.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row != hoverRow[0]) {
                    hoverRow[0] = row;
                    table.repaint();
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow[0] = -1;
                table.repaint();
            }
        });
    }

    private static void installMoneyColumnRenderers(JTable table) {
        for (int col = 0; col < table.getColumnCount(); col++) {
            String name = table.getColumnName(col).toLowerCase(Locale.ROOT);
            if (name.contains("price") || name.contains("payment") || name.contains("cost")
                    || name.contains("total") || name.contains("amount") && name.contains("sale")) {
                table.getColumnModel().getColumn(col).setCellRenderer(new DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(
                            JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column
                    ) {
                        Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                        setHorizontalAlignment(SwingConstants.RIGHT);
                        setFont(fontTabular(13));
                        if (value instanceof Number n) {
                            setText(String.format(Locale.US, "$%,.2f", n.doubleValue()));
                        }
                        if (!isSelected) {
                            c.setBackground(row % 2 == 0 ? SURFACE : SURFACE_ELEVATED);
                        }
                        return c;
                    }
                });
            }
        }
    }

    public static void styleScrollPane(JScrollPane scroll) {
        if (scroll == null) {
            return;
        }
        scroll.setBorder(lineBorder());
        scroll.getViewport().setBackground(BACKGROUND);
        scroll.setBackground(BACKGROUND);
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
    }

    public static void applyPanelBackground(JComponent component) {
        styleTree(component);
    }

    public static void styleWindow(Window window) {
        if (window == null) {
            return;
        }
        applyWindowChrome(window);
        if (window instanceof RootPaneContainer rpc) {
            styleTree(rpc.getRootPane());
        } else {
            for (Component component : window.getComponents()) {
                styleTree(component);
            }
        }
    }

    private static void styleTree(Component component) {
        styleComponent(component);
        if (component instanceof JComponent jc) {
            for (Component child : jc.getComponents()) {
                styleTree(child);
            }
        }
    }

    private static void styleComponent(Component component) {
        if (component instanceof JButton button) {
            Object themedFg = button.getClientProperty(CLIENT_BUTTON_FG);
            if (themedFg instanceof Color fg) {
                button.setForeground(fg);
            } else if (button.getClientProperty(CLIENT_BUTTON_BASE) == null
                    && !PRIMARY.equals(button.getBackground())) {
                button.setBackground(SURFACE_ELEVATED);
                button.setForeground(TEXT);
            }
        } else if (component instanceof JTextField field) {
            field.setBackground(INPUT);
            field.setForeground(TEXT);
            field.setCaretColor(TEXT);
            if (!(field instanceof JPasswordField) && field.getBorder() == null) {
                field.setBorder(inputBorder());
            }
        } else if (component instanceof JPasswordField passwordField) {
            passwordField.setBackground(INPUT);
            passwordField.setForeground(TEXT);
            passwordField.setCaretColor(TEXT);
            passwordField.setBorder(inputBorder());
        } else if (component instanceof JTextArea textArea) {
            textArea.setForeground(TEXT);
            textArea.setCaretColor(TEXT);
            if (textArea.isEditable()) {
                textArea.setBackground(INPUT);
                textArea.setBorder(inputBorder());
            }
        } else if (component instanceof JComboBox<?> comboBox) {
            comboBox.setBackground(INPUT);
            comboBox.setForeground(TEXT);
            comboBox.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(
                        JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus
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
            polishTable(table);
        } else if (component instanceof JScrollPane scrollPane) {
            styleScrollPane(scrollPane);
        } else if (component instanceof JLabel label) {
            if (!Boolean.TRUE.equals(label.getClientProperty(CLIENT_PRESERVE_FOREGROUND))) {
                label.setForeground(TEXT);
            }
        } else if (component instanceof JComponent jc) {
            Object surface = jc.getClientProperty(CLIENT_SURFACE);
            if ("card".equals(surface)) {
                jc.setBackground(SURFACE);
            } else {
                jc.setBackground(BACKGROUND);
            }
            jc.setForeground(TEXT);
        } else {
            component.setBackground(BACKGROUND);
            component.setForeground(TEXT);
        }
    }

    public static JLabel createBadge(String text, Color bg, Color fg) {
        JLabel badge = new JLabel("  " + text + "  ") {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setOpaque(false);
        badge.setForeground(fg);
        badge.setFont(fontSemiBold(11));
        badge.putClientProperty(CLIENT_PRESERVE_FOREGROUND, Boolean.TRUE);
        return badge;
    }

    public static Color brighten(Color c, int delta) {
        return new Color(
                Math.min(255, c.getRed() + delta),
                Math.min(255, c.getGreen() + delta),
                Math.min(255, c.getBlue() + delta),
                c.getAlpha());
    }

    public static Color darken(Color c, int delta) {
        return new Color(
                Math.max(0, c.getRed() - delta),
                Math.max(0, c.getGreen() - delta),
                Math.max(0, c.getBlue() - delta),
                c.getAlpha());
    }

    private static void installNimbusLookAndFeel() {
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                         UnsupportedLookAndFeelException ignored) {
                }
                break;
            }
        }
    }

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
