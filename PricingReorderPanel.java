import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;


/** Extracted from WorkspaceShell. */
public final class PricingReorderPanel {
    private PricingReorderPanel() {}

        /** Bold SKU code with a wrapping name region so labels are not abbreviated to tiny fragments. */
    static final int BULK_EDIT_META_VGAP = 2;

        static JLabel bulkEditSkuCodeLabel(String code) {
        JLabel jc = new JLabel(code);
        jc.setFont(jc.getFont().deriveFont(Font.BOLD, 12f));
        jc.setOpaque(false);
        return jc;
    }

        static JTextArea bulkEditSkuNameArea(String name) {
        String safeName = Objects.toString(name, "");
        JTextArea nameArea = new JTextArea(safeName);
        nameArea.setEditable(false);
        nameArea.setFocusable(false);
        nameArea.setOpaque(false);
        nameArea.setLineWrap(true);
        nameArea.setWrapStyleWord(true);
        nameArea.setRows(2);
        nameArea.setFont(nameArea.getFont().deriveFont(Font.PLAIN, 11f));
        nameArea.setForeground(AppUI.TEXT_MUTED);
        nameArea.setBorder(BorderFactory.createEmptyBorder());
        return nameArea;
    }

        /** Tweaks preferred width while typing so compact numeric editors grow within bounds. */
    static void attachAdaptiveBoundedFieldWidth(JTextField field, int heightPx, int minWidthPx, int maxWidthPx) {
        Runnable sync = () -> {
            FontMetrics fm = field.getFontMetrics(field.getFont());
            String s = field.getText();
            if (s == null) {
                s = "";
            }
            int measured = fm.stringWidth(s.isEmpty() ? "0" : s) + 32;
            int w = Math.min(maxWidthPx, Math.max(minWidthPx, measured));
            field.setPreferredSize(new Dimension(w, heightPx));
            field.setMaximumSize(new Dimension(maxWidthPx, heightPx));
            Component p = field.getParent();
            if (p != null) {
                p.revalidate();
            }
        };
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                sync.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                sync.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                sync.run();
            }
        });
        sync.run();
    }

        /** Select entire field on focus so typing replaces the loaded value instead of appending. */
    static void attachSelectAllOnFocus(JTextField field) {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (!e.isTemporary()) {
                    SwingUtilities.invokeLater(field::selectAll);
                }
            }
        });
    }

        static final int BULK_EDIT_FIELD_COL_WIDTH = 108;

        /** Compact input column aligned under header labels. */
    static JPanel buildBulkEditFieldColumn(JTextField field, int minFieldW, int maxFieldW) {
        JPanel col = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        col.setOpaque(false);
        col.setPreferredSize(new Dimension(BULK_EDIT_FIELD_COL_WIDTH, WorkspaceShell.INPUT_HEIGHT));
        WorkspaceShell.styleInput(field);
        attachAdaptiveBoundedFieldWidth(field, WorkspaceShell.INPUT_HEIGHT, minFieldW, maxFieldW);
        attachSelectAllOnFocus(field);
        col.add(field);
        return col;
    }

        /** Small left-aligned muted column header that lines up over its item-row column. */
    static JLabel bulkEditColumnHeader(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(AppUI.TEXT_MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

        /** Column headers left-aligned over the item, market-price, and reorder columns below. */
    static JPanel buildPricingReorderHeaderRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppUI.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 0);
        row.add(bulkEditColumnHeader("Item"), gbc);

        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 1;
        gbc.insets = new Insets(0, 16, 0, 0);
        JLabel marketHeader = bulkEditColumnHeader("Market price");
        marketHeader.setPreferredSize(new Dimension(BULK_EDIT_FIELD_COL_WIDTH, marketHeader.getPreferredSize().height));
        row.add(marketHeader, gbc);

        gbc.gridx = 2;
        JLabel reorderHeader = bulkEditColumnHeader("Reorder at");
        reorderHeader.setPreferredSize(new Dimension(BULK_EDIT_FIELD_COL_WIDTH, reorderHeader.getPreferredSize().height));
        row.add(reorderHeader, gbc);
        capRowHeight(row);
        return row;
    }

        /** One inventory row: SKU code on top, name + input fields aligned on the row below. */
    static JPanel buildPricingReorderItemRow(
            String code,
            String name,
            JTextField marketField,
            JTextField reorderField) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppUI.BORDER),
                BorderFactory.createEmptyBorder(10, 8, 10, 8)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 0);
        row.add(bulkEditSkuCodeLabel(code), gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(BULK_EDIT_META_VGAP, 0, 0, 0);
        row.add(bulkEditSkuNameArea(name), gbc);

        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 1;
        gbc.insets = new Insets(BULK_EDIT_META_VGAP, 16, 0, 0);
        row.add(buildBulkEditFieldColumn(marketField, 56, 96), gbc);

        gbc.gridx = 2;
        reorderField.setHorizontalAlignment(JTextField.TRAILING);
        row.add(buildBulkEditFieldColumn(reorderField, 52, 88), gbc);
        capRowHeight(row);
        return row;
    }

        /** Locks a stacked-list row to its preferred height so BoxLayout cannot stretch gaps when the window is tall. */
    static void capRowHeight(JComponent row) {
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
    }

        /**
     * Bulk-edit market price and reorder trigger on one screen — both fields sit on the same row per SKU.
     * Submit applies only rows where a value actually changed.
     */
    public static JPanel build(User user, Connection connection, JPanel workspaceContainer)
            throws SQLException {
        WorkspaceShell.ensureAdmin(user, "Pricing & Reorder");
        JPanel panel = WorkspaceShell.buildFormPanel("Pricing & reorder");
        JPanel centerWrap = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(centerWrap);

        JPanel intro = WorkspaceShell.buildSectionPanel();
        intro.add(WorkspaceShell.buildSectionText(
                "Each row lists the item code and name with market price and reorder threshold side by side. "
                        + "Click a field to select its current value, then type to replace it. "
                        + "Leave market price blank to keep the saved value. Submit saves only rows you actually changed."));
        centerWrap.add(intro, BorderLayout.NORTH);

        LinkedHashMap<String, Double> originalPrice = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> originalTriggers = new LinkedHashMap<>();
        LinkedHashMap<String, JTextField> codeToPriceField = new LinkedHashMap<>();
        LinkedHashMap<String, JTextField> codeToTriggerField = new LinkedHashMap<>();
        List<String> codesOrdered = new ArrayList<>();
        List<String> namesOrdered = new ArrayList<>();

        boolean anyRows = false;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Item Code`, `Item Name`, `Stock`, `Market Price`, `ReOrder Trigger` "
                        + "FROM inventory ORDER BY `Item Code` ASC"
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    anyRows = true;
                    String code = rs.getString("Item Code");
                    String nm = Objects.toString(rs.getString("Item Name"), "");
                    double mp = rs.getDouble("Market Price");
                    boolean mpNull = rs.wasNull();
                    int trigger = rs.getInt("ReOrder Trigger");

                    originalPrice.put(code, mpNull ? null : mp);
                    originalTriggers.put(code, trigger);

                    JTextField priceField = new JTextField(mpNull ? "" : String.format(Locale.US, "%.2f", mp), 8);
                    JTextField triggerField = new JTextField(Integer.toString(trigger), 8);
                    codeToPriceField.put(code, priceField);
                    codeToTriggerField.put(code, triggerField);
                    codesOrdered.add(code);
                    namesOrdered.add(nm);
                }
            }
        }

        if (!anyRows) {
            centerWrap.add(WorkspaceShell.buildSectionText("No inventory rows found."), BorderLayout.CENTER);
        } else {
            JPanel scrollBody = new JPanel();
            scrollBody.setLayout(new BoxLayout(scrollBody, BoxLayout.Y_AXIS));
            scrollBody.setOpaque(true);
            AppUI.applyPanelBackground(scrollBody);

            scrollBody.add(buildPricingReorderHeaderRow());
            for (int i = 0; i < codesOrdered.size(); i++) {
                String code = codesOrdered.get(i);
                scrollBody.add(buildPricingReorderItemRow(
                        code,
                        namesOrdered.get(i),
                        codeToPriceField.get(code),
                        codeToTriggerField.get(code)));
            }
            scrollBody.add(Box.createVerticalGlue());

            JScrollPane scroll = new JScrollPane(scrollBody,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(AppUI.newRoundedBorder(8));
            scroll.getViewport().setBackground(scrollBody.getBackground());
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.setPreferredSize(new Dimension(1080, Math.min(WorkspaceShell.MAIN_FRAME_BASE_H - 260, 520)));
            centerWrap.add(scroll, BorderLayout.CENTER);
        }

        JButton submit = new JButton("Save pricing & reorder changes");
        AppUI.stylePrimaryButton(submit);
        submit.setEnabled(anyRows);
        submit.addActionListener(e -> {
            List<Object[]> priceUpdates = new ArrayList<>();
            List<Object[]> triggerUpdates = new ArrayList<>();

            for (String code : codesOrdered) {
                JTextField priceField = codeToPriceField.get(code);
                try {
                    Double v = WorkspaceShell.parseOptionalMarketPriceInput(priceField.getText());
                    if (v != null) {
                        Double prior = originalPrice.get(code);
                        if (prior == null || Math.abs(prior - v) >= 1e-9) {
                            priceUpdates.add(new Object[]{code, v});
                        }
                    }
                } catch (NumberFormatException nf) {
                    JOptionPane.showMessageDialog(panel,
                            "Invalid price for " + code + ": " + nf.getMessage(),
                            "Input error",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }

                JTextField triggerField = codeToTriggerField.get(code);
                int original = originalTriggers.get(code);
                String raw = triggerField.getText().trim();
                int newVal;
                try {
                    if (raw.isEmpty()) {
                        JOptionPane.showMessageDialog(panel,
                                "Reorder trigger cannot be blank for " + code + ".",
                                "Input error",
                                JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    newVal = Integer.parseInt(raw);
                } catch (NumberFormatException nf) {
                    JOptionPane.showMessageDialog(panel,
                            "Invalid reorder trigger for " + code + ": enter a whole number.",
                            "Input error",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (newVal < 0) {
                    JOptionPane.showMessageDialog(panel,
                            "Reorder trigger cannot be negative for " + code + ".",
                            "Input error",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (newVal != original) {
                    triggerUpdates.add(new Object[]{code, newVal});
                }
            }

            if (priceUpdates.isEmpty() && triggerUpdates.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Nothing to save — no prices or reorder triggers were changed.");
                return;
            }

            boolean savedAc = true;
            try {
                savedAc = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (PreparedStatement pricePs = connection.prepareStatement(
                        "UPDATE inventory SET `Market Price` = ? WHERE `Item Code` = ?");
                     PreparedStatement triggerPs = connection.prepareStatement(
                             "UPDATE inventory SET `ReOrder Trigger` = ? WHERE `Item Code` = ?");
                     PreparedStatement movement = connection.prepareStatement(
                             "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (Object[] row : priceUpdates) {
                        String code = (String) row[0];
                        Double newPrice = (Double) row[1];
                        Double prior = originalPrice.get(code);
                        pricePs.setDouble(1, newPrice);
                        pricePs.setString(2, code);
                        pricePs.executeUpdate();
                        InventoryAudit.touchMarketPriceUpdated(connection, code);
                        String beforeText = prior == null ? "null" : String.format(Locale.US, "%.4f", prior);
                        String afterText = String.format(Locale.US, "%.4f", newPrice);
                        InventoryAudit.logChange(connection, user.getUsername(), code,
                                InventoryAudit.CHANGE_MARKET_PRICE, 0,
                                "BULK_MARKET_PRICE",
                                "from=" + beforeText + " to=" + afterText);
                    }
                    for (Object[] row : triggerUpdates) {
                        String code = (String) row[0];
                        int newVal = (Integer) row[1];
                        triggerPs.setInt(1, newVal);
                        triggerPs.setString(2, code);
                        triggerPs.executeUpdate();
                        movement.setString(1, code);
                        movement.setString(2, " ");
                        movement.setString(3, "UPDATED TRIGGER");
                        movement.setString(4, "REORDER_TRIGGER_UPDATE");
                        movement.setString(5, user.getUsername());
                        movement.setString(6, dateTime.nowDisplayString());
                        movement.executeUpdate();
                    }
                }
                connection.commit();
                StringBuilder msg = new StringBuilder("Saved changes");
                if (!priceUpdates.isEmpty()) {
                    msg.append(" — ").append(priceUpdates.size()).append(" market price(s)");
                }
                if (!triggerUpdates.isEmpty()) {
                    msg.append(priceUpdates.isEmpty() ? " — " : ", ").append(triggerUpdates.size()).append(" reorder trigger(s)");
                }
                msg.append(".");
                JOptionPane.showMessageDialog(panel, msg.toString());
                WorkspaceShell.refreshActiveMetricsStripNow();
                try {
                    WorkspaceShell.showView(workspaceContainer, "Pricing & Reorder", build(user, connection, workspaceContainer));
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(panel,
                            "Changes saved, but the screen could not refresh: " + ex.getMessage());
                }
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // ignore rollback failure
                }
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            } finally {
                try {
                    connection.setAutoCommit(savedAc);
                } catch (SQLException ignored) {
                    // ignore
                }
            }
        });

        JPanel footer = WorkspaceShell.buildActionBar(null, submit);
        panel.add(centerWrap, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }
}
