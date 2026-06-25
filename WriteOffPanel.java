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
public final class WriteOffPanel {
    private WriteOffPanel() {}

        /** Builds admin-only write-off panel for stock reductions. */
    public static JPanel build(User user, Connection connection) {
        WorkspaceShell.ensureAdmin(user, "Write Off Stock");
        JPanel panel = WorkspaceShell.buildFormPanel("Write Off Stock");
        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints rg = new GridBagConstraints();
        rg.insets = new Insets(4, 0, 4, 10);

        JTextField itemCode = new JTextField();
        JTextField itemDesc = new JTextField();
        JTextField quantity = new JTextField();
        JComboBox<String> reasonCode = new JComboBox<>(new String[]{
                "DAMAGED",
                "EXPIRED",
                "COUNT_CORRECTION",
                "THEFT_LOSS",
                "RETURN_DAMAGED",
                "OTHER"
        });
        WorkspaceShell.styleInput(itemCode, itemDesc, quantity);
        WorkspaceShell.styleAutoFilledInventoryField(itemDesc);
        WorkspaceShell.styleComboMatchInputRow(reasonCode);

        int row = 0;
        rg.gridx = 0;
        rg.gridy = row;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Item Code *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(itemCode, rg);
        row++;
        rg.gridx = 0;
        rg.gridy = row;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Item Description"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        itemDesc.setToolTipText("From inventory row for the entered item code.");
        form.add(itemDesc, rg);
        row++;
        rg.gridx = 0;
        rg.gridy = row;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Write-off Quantity *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(quantity, rg);
        row++;
        rg.gridx = 0;
        rg.gridy = row;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Reason Code *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(reasonCode, rg);

        WorkspaceShell.wireInventoryItemDescriptionLookup(connection, itemCode, itemDesc);

        DefaultTableModel writeOffHistoryModel = new DefaultTableModel(
                new String[]{"Item Code", "Item Description", "Amount", "Reason", "User", "Date"},
                0
        );
        JTable writeOffHistoryTable = new JTable(writeOffHistoryModel);
        WorkspaceShell.installTableCopyMenu(writeOffHistoryTable);
        TableRowSorter<DefaultTableModel> writeOffSorter = new TableRowSorter<>(writeOffHistoryModel);
        writeOffHistoryTable.setRowSorter(writeOffSorter);
        try {
            loadWriteOffHistory(writeOffHistoryModel, connection);
            WorkspaceShell.deferPackTableColumns(writeOffHistoryTable);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panel, "Unable to load write-off history: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        JScrollPane historyScroll = new JScrollPane(writeOffHistoryTable);
        historyScroll.setBorder(AppUI.newRoundedBorder(8));

        JButton submit = new JButton("Write Off");
        AppUI.stylePrimaryButton(submit);
        submit.addActionListener(e -> {
            String code = itemCode.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Item code is required.");
                return;
            }
            String selectedReason = (String) reasonCode.getSelectedItem();
            if (selectedReason == null || selectedReason.isBlank()) {
                JOptionPane.showMessageDialog(panel, "Reason code is required.");
                return;
            }
            try {
                int qty = Integer.parseInt(quantity.getText().trim());
                if (qty < 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be zero or greater.");
                    return;
                }
                boolean savedAc;
                try {
                    savedAc = connection.getAutoCommit();
                    connection.setAutoCommit(false);
                } catch (SQLException acSetup) {
                    JOptionPane.showMessageDialog(panel,
                            "Could not prepare the database transaction: " + acSetup.getMessage(),
                            "Database",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                boolean persisted = false;
                try {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE Inventory SET Stock = Stock - ? WHERE `Item Code` = ? AND Stock >= ?")) {
                        update.setInt(1, qty);
                        update.setString(2, code);
                        update.setInt(3, qty);
                        if (update.executeUpdate() == 0) {
                            connection.rollback();
                            JOptionPane.showMessageDialog(panel,
                                    "Write-off failed. Check item code and available stock.");
                            return;
                        }
                    }
                    WorkspaceShell.deductInventoryStorageQtySpread(connection, code, qty);
                    try (PreparedStatement movement = connection.prepareStatement(
                            "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                        movement.setString(1, code);
                        movement.setString(2, String.valueOf(qty));
                        movement.setString(3, "WRITE OFF");
                        movement.setString(4, selectedReason);
                        movement.setString(5, user.getUsername());
                        movement.setString(6, dateTime.nowDisplayString());
                        movement.executeUpdate();
                    }
                    connection.commit();
                    persisted = true;
                } catch (SQLException db) {
                    try {
                        connection.rollback();
                    } catch (SQLException suppressed) {
                        db.addSuppressed(suppressed);
                    }
                    JOptionPane.showMessageDialog(panel, "Database error: " + db.getMessage(), "Database",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    try {
                        connection.setAutoCommit(savedAc);
                    } catch (SQLException acEx) {
                        JOptionPane.showMessageDialog(panel,
                                "Unable to restore connection state after write-off; restart the session. " + acEx.getMessage(),
                                "Database",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
                if (persisted) {
                    JOptionPane.showMessageDialog(panel, "Stock write-off completed.");
                    try {
                        loadWriteOffHistory(writeOffHistoryModel, connection);
                        WorkspaceShell.deferPackTableColumns(writeOffHistoryTable);
                    } catch (SQLException reloadEx) {
                        JOptionPane.showMessageDialog(panel, "Saved, but history table could not refresh: " + reloadEx.getMessage(),
                                "Warning", JOptionPane.WARNING_MESSAGE);
                    }
                    WorkspaceShell.requestMetricsRefresh();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid whole number.");
            }
        });
        JPanel footer = WorkspaceShell.buildActionBar(null, submit);
        panel.add(form, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(center);
        center.add(historyScroll, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

        /**
     * Loads historical write-off movements into the supplied table model.
     *
     * @param model target table model
     * @param connection active database connection
     * @throws SQLException when query fails
     */
    static void loadWriteOffHistory(DefaultTableModel model, Connection connection) throws SQLException {
        model.setRowCount(0);
        String sql = """
                SELECT m.`Item` AS `Item Code`,
                       COALESCE(inv.`Item Name`, '') AS `Item Description`,
                       m.`Amount`,
                       COALESCE(m.`Reason`, '') AS `Reason`,
                       m.`User`,
                       m.`Date`
                FROM movements m
                LEFT JOIN inventory inv ON inv.`Item Code` = m.`Item`
                WHERE m.`Type` = 'WRITE OFF'
                ORDER BY m.`Date` DESC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getString("Item Code"),
                        rs.getString("Item Description"),
                        rs.getInt("Amount"),
                        rs.getString("Reason"),
                        rs.getString("User"),
                        rs.getString("Date")
                });
            }
        }
    }
}
