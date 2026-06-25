import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public final class StorageLocationsPanel {
    private StorageLocationsPanel() {}

    /** Manage named bins; {@link DatabaseManager#STORAGE_LOCATION_UNASSIGNED_ID Unassigned} is fixed in the database for untracked stock. */
    public static JPanel build(Connection connection) throws SQLException {
        DatabaseManager.ensureStorageLocationsAndBuckets(connection);
        JPanel panel = WorkspaceShell.buildFormPanel("Storage Locations");
        JPanel intro = WorkspaceShell.buildSectionPanel();
        intro.add(WorkspaceShell.buildSectionText(
                "Shelf labels only — totals still live on Stock. Deletes are blocked until the bin has no qty. "
                        + "\"Unassigned\" cannot be renamed or deleted. Select a bin to see SKUs aggregated on the right."));
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            intro.add(Box.createVerticalStrut(12));
            intro.add(WorkspaceShell.buildSectionText("Storage tables are not available on this database file."));
            panel.add(intro, BorderLayout.NORTH);
            return panel;
        }

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"ID", "Name", "Active"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        WorkspaceShell.installTableCopyMenu(table);
        WorkspaceShell.deferPackTableColumns(table);

        DefaultTableModel binDetailModel = new DefaultTableModel(
                new String[]{"Item code", "Item name", "Qty in bin"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable binDetailTable = new JTable(binDetailModel);
        binDetailTable.setAutoCreateRowSorter(true);
        WorkspaceShell.installTableCopyMenu(binDetailTable);
        WorkspaceShell.deferPackTableColumns(binDetailTable);

        JLabel binDetailHeading = new JLabel("Select a bin on the left to see its contents.");

        Runnable refreshBinContentsForSelection = () -> {
            try {
                int vr = table.getSelectedRow();
                if (vr < 0) {
                    binDetailModel.setRowCount(0);
                    binDetailHeading.setText("Select a bin on the left to see its contents.");
                    return;
                }
                int mr = table.convertRowIndexToModel(vr);
                int locId = Integer.parseInt(Objects.toString(model.getValueAt(mr, 0), "-1"));
                String locName = Objects.toString(model.getValueAt(mr, 1), "");
                reloadStorageBinContentsTable(binDetailModel, connection, locId);
                WorkspaceShell.deferPackTableColumns(binDetailTable);
                binDetailHeading.setText(
                        locName.isBlank() ? ("Bin contents · #" + locId) : ("Bin contents · " + locName));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel,
                        "Unable to load bin contents: " + ex.getMessage(),
                        "Database",
                        JOptionPane.ERROR_MESSAGE);
            }
        };

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            refreshBinContentsForSelection.run();
        });

        Runnable reloadTable = () -> {
            try {
                int keepId = -1;
                int sr = table.getSelectedRow();
                if (sr >= 0) {
                    int mr = table.convertRowIndexToModel(sr);
                    keepId = Integer.parseInt(Objects.toString(model.getValueAt(mr, 0), "-1"));
                }
                reloadStorageLocationsIntoTable(model, connection);
                WorkspaceShell.deferPackTableColumns(table);
                table.clearSelection();
                if (keepId >= 0) {
                    boolean found = false;
                    for (int mr = 0; mr < model.getRowCount(); mr++) {
                        int rowLocId = Integer.parseInt(Objects.toString(model.getValueAt(mr, 0), "-999"));
                        if (rowLocId == keepId) {
                            int vr = table.convertRowIndexToView(mr);
                            if (vr >= 0) {
                                table.getSelectionModel().setSelectionInterval(vr, vr);
                                found = true;
                            }
                            break;
                        }
                    }
                    if (!found) {
                        binDetailModel.setRowCount(0);
                        binDetailHeading.setText("Select a bin on the left to see its contents.");
                    }
                } else {
                    binDetailModel.setRowCount(0);
                    binDetailHeading.setText("Select a bin on the left to see its contents.");
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Unable to refresh locations: " + ex.getMessage(), "Database",
                        JOptionPane.ERROR_MESSAGE);
            }
        };

        JTextField nameField = new JTextField();
        WorkspaceShell.styleInput(nameField);

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 0, 4, 8);
        gc.gridy = 0;
        gc.gridx = 0;
        gc.anchor = GridBagConstraints.LINE_END;
        form.add(new JLabel("New bin name"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        gc.anchor = GridBagConstraints.LINE_START;
        form.add(nameField, gc);

        JButton addBtn = new JButton("Add bin");
        AppUI.stylePrimaryButton(addBtn);
        addBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isBlank()) {
                JOptionPane.showMessageDialog(panel, "Bin name cannot be blank.");
                return;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    """
                            INSERT INTO storage_locations (name, active, system_reserved)
                            VALUES (?,?,0)
                            """)) {
                insert.setString(1, name);
                insert.setInt(2, 1);
                insert.executeUpdate();
                nameField.setText("");
                reloadTable.run();
            } catch (SQLException ex) {
                String msg = ex.getMessage();
                if (msg != null && msg.toUpperCase(Locale.ROOT).contains("UNIQUE")) {
                    JOptionPane.showMessageDialog(panel,
                            "That name is already taken (names are compared case-insensitively).");
                } else {
                    JOptionPane.showMessageDialog(panel, "Unable to add bin: " + ex.getMessage(), "Database",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton renameBtn = new JButton("Rename selected…");
        WorkspaceShell.styleSecondaryButton(renameBtn);
        renameBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(panel, "Select a row first.");
                return;
            }
            int mr = table.convertRowIndexToModel(row);
            int id = Integer.parseInt(Objects.toString(model.getValueAt(mr, 0), ""));
            String current = Objects.toString(model.getValueAt(mr, 1), "");
            if (id == DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID) {
                JOptionPane.showMessageDialog(panel, "\"Unassigned\" cannot be renamed.");
                return;
            }
            String next = JOptionPane.showInputDialog(panel, "Rename bin", current);
            if (next == null) {
                return;
            }
            String trimmed = next.trim();
            if (trimmed.isBlank()) {
                JOptionPane.showMessageDialog(panel, "Name cannot be blank.");
                return;
            }
            try (PreparedStatement up = connection.prepareStatement(
                    """
                            UPDATE storage_locations SET name = ?
                            WHERE id = ? AND id <> ? AND system_reserved = 0
                            """)) {
                up.setString(1, trimmed);
                up.setInt(2, id);
                up.setInt(3, DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID);
                if (up.executeUpdate() == 0) {
                    JOptionPane.showMessageDialog(panel, "Rename failed.");
                    return;
                }
                reloadTable.run();
            } catch (SQLException ex) {
                String msg = ex.getMessage();
                if (msg != null && msg.toUpperCase(Locale.ROOT).contains("UNIQUE")) {
                    JOptionPane.showMessageDialog(panel, "That name is already taken.");
                } else {
                    JOptionPane.showMessageDialog(panel, "Rename failed: " + ex.getMessage(), "Database",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton deleteBtn = new JButton("Delete selected");
        WorkspaceShell.styleSecondaryButton(deleteBtn);
        deleteBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(panel, "Select a row first.");
                return;
            }
            int mr = table.convertRowIndexToModel(row);
            int id = Integer.parseInt(Objects.toString(model.getValueAt(mr, 0), ""));
            if (id == DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID) {
                JOptionPane.showMessageDialog(panel, "\"Unassigned\" cannot be deleted.");
                return;
            }
            int ok = JOptionPane.showConfirmDialog(panel, "Delete this bin?", "Confirm", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) {
                return;
            }
            try (PreparedStatement delete = connection.prepareStatement("""
                    DELETE FROM storage_locations
                    WHERE id = ? AND id <> ? AND system_reserved = 0 AND NOT EXISTS (
                        SELECT 1 FROM inventory_storage_qty WHERE location_id = ?
                    )
                    """)) {
                int uid = DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID;
                delete.setInt(1, id);
                delete.setInt(2, uid);
                delete.setInt(3, id);
                if (delete.executeUpdate() == 0) {
                    JOptionPane.showMessageDialog(panel,
                            "Delete blocked — bin still holds stock or is the fixed Unassigned bucket.");
                    return;
                }
                reloadTable.run();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Delete failed: " + ex.getMessage(), "Database",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton toggleActiveBtn = new JButton("Toggle active");
        WorkspaceShell.styleSecondaryButton(toggleActiveBtn);
        toggleActiveBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(panel, "Select a row first.");
                return;
            }
            int mr = table.convertRowIndexToModel(row);
            int id = Integer.parseInt(Objects.toString(model.getValueAt(mr, 0), ""));
            if (id == DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID) {
                JOptionPane.showMessageDialog(panel, "\"Unassigned\" cannot be deactivated.");
                return;
            }
            try (PreparedStatement up = connection.prepareStatement("""
                        UPDATE storage_locations
                        SET active = CASE WHEN active = 1 THEN 0 ELSE 1 END
                        WHERE id = ? AND system_reserved = 0
                        """)) {
                up.setInt(1, id);
                if (up.executeUpdate() == 0) {
                    JOptionPane.showMessageDialog(panel, "Protected system bins cannot be toggled.");
                    return;
                }
                reloadTable.run();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Toggle failed: " + ex.getMessage(), "Database",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton refreshBtn = new JButton("Refresh");
        WorkspaceShell.styleSecondaryButton(refreshBtn);
        refreshBtn.addActionListener(ae -> reloadTable.run());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(actions);
        actions.add(addBtn);
        actions.add(renameBtn);
        actions.add(deleteBtn);
        actions.add(toggleActiveBtn);
        actions.add(refreshBtn);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AppUI.newRoundedBorder(8));
        scroll.setMinimumSize(new Dimension(200, 160));

        JScrollPane binDetailScroll = new JScrollPane(binDetailTable);
        binDetailScroll.setBorder(AppUI.newRoundedBorder(8));
        binDetailScroll.setMinimumSize(new Dimension(160, 160));

        JPanel binDetailWrap = new JPanel(new BorderLayout(0, 6));
        AppUI.applyPanelBackground(binDetailWrap);
        binDetailHeading.setForeground(AppUI.TEXT_MUTED);
        binDetailHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
        binDetailWrap.add(binDetailHeading, BorderLayout.NORTH);
        binDetailWrap.add(binDetailScroll, BorderLayout.CENTER);
        binDetailWrap.setMinimumSize(new Dimension(200, 80));

        JSplitPane locSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, binDetailWrap);
        locSplit.setResizeWeight(2.0 / 3.0);
        locSplit.setContinuousLayout(true);
        locSplit.setBorder(null);
        locSplit.setDividerSize(6);
        locSplit.setMinimumSize(new Dimension(420, 200));
        SwingUtilities.invokeLater(() -> {
            int w = locSplit.getWidth();
            if (w > 0) {
                locSplit.setDividerLocation(Math.max(200, (int) (w * (2.0 / 3.0))));
            } else {
                locSplit.setDividerLocation(2.0 / 3.0);
            }
        });

        JPanel top = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(top);
        top.add(intro, BorderLayout.NORTH);
        top.add(locSplit, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(footer);
        footer.add(form, BorderLayout.NORTH);
        footer.add(actions, BorderLayout.CENTER);

        reloadTable.run();
        panel.add(top, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * One row per SKU in a bin with {@code SUM(qty)} so duplicates collapse if they ever exist for the same bin.
     */
    static void reloadStorageBinContentsTable(
            DefaultTableModel detailModel,
            Connection connection,
            int locationId
    ) throws SQLException {
        detailModel.setRowCount(0);
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT s.item_code AS item_code,
                       MAX(COALESCE(i.`Item Name`, '')) AS item_name,
                       SUM(s.qty) AS total_qty
                FROM inventory_storage_qty s
                LEFT JOIN inventory i ON i.`Item Code` = s.item_code
                WHERE s.location_id = ? AND s.qty > 0
                GROUP BY s.item_code
                ORDER BY s.item_code COLLATE NOCASE ASC
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    detailModel.addRow(new Object[]{
                            rs.getString("item_code"),
                            rs.getString("item_name"),
                            rs.getInt("total_qty")
                    });
                }
            }
        }
    }

        static void reloadStorageLocationsIntoTable(DefaultTableModel model, Connection connection) throws SQLException {
        model.setRowCount(0);
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                """
                        SELECT id, name, active FROM storage_locations
                        ORDER BY sort_order ASC, name COLLATE NOCASE ASC
                        """)) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("active") != 0 ? "Yes" : "No"
                    });
                }
            }
        }
    }
}
