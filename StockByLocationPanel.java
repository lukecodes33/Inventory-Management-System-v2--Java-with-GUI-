import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

public final class StockByLocationPanel {
    private StockByLocationPanel() {}

    /** Shelf placement report filtered by SKU text / optional bin. */
    public static JPanel build(User user, Connection connection) throws SQLException {
        DatabaseManager.ensureStorageLocationsAndBuckets(connection);
        JPanel panel = WorkspaceShell.buildFormPanel("Stock by Location");
        JLabel hint = WorkspaceShell.buildSectionText(
                "Search by item code or name substring. Select a row to move qty to another bin.");

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Item code", "Item name", "Location", "Qty in bin", "Total stock"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        WorkspaceShell.installTableCopyMenu(table);
        WorkspaceShell.deferPackTableColumns(table);

        JTextField filter = new JTextField();
        WorkspaceShell.styleInput(filter);
        JComboBox<WorkspaceShell.StorageLocationPick> locPick = new JComboBox<>();

        Runnable reload = () -> {
            Object sel = locPick.getSelectedItem();
            int lid = sel instanceof WorkspaceShell.StorageLocationPick sp ? sp.id : WorkspaceShell.STOCK_REPORT_ALL_LOCATIONS_ID;
            try {
                reloadStockByLocationTable(model, connection, lid, filter.getText().trim());
                WorkspaceShell.deferPackTableColumns(table);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Could not load report: " + ex.getMessage(), "Database",
                        JOptionPane.ERROR_MESSAGE);
            }
        };

        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            JPanel fallback = WorkspaceShell.buildSectionPanel();
            fallback.add(hint);
            fallback.add(Box.createVerticalStrut(10));
            fallback.add(WorkspaceShell.buildSectionText("Storage tables are not available."));
            panel.add(fallback, BorderLayout.NORTH);
            return panel;
        }

        refreshStockReportLocationCombo(locPick, connection);
        reload.run();

        filter.getDocument().addDocumentListener(new DocumentListener() {
            private void go() {
                reload.run();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                go();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                go();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                go();
            }
        });
        locPick.addActionListener(e -> reload.run());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AppUI.newRoundedBorder(8));

        JPanel top = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(top);
        top.add(hint, BorderLayout.NORTH);
        top.add(scroll, BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(controls);
        GridBagConstraints cg = new GridBagConstraints();
        cg.insets = WorkspaceShell.FORM_GRID_INSETS;
        cg.anchor = GridBagConstraints.WEST;
        cg.fill = GridBagConstraints.HORIZONTAL;
        cg.gridx = 0;
        cg.gridy = 0;
        cg.weightx = 0;
        controls.add(WorkspaceShell.buildFormLabel("Location"), cg);
        cg.gridx = 1;
        cg.weightx = 1;
        WorkspaceShell.styleComboMatchInputRow(locPick);
        controls.add(locPick, cg);
        cg.gridx = 0;
        cg.gridy = 1;
        cg.weightx = 0;
        controls.add(WorkspaceShell.buildFormLabel("Item filter"), cg);
        cg.gridx = 1;
        cg.weightx = 1;
        WorkspaceShell.styleInput(filter);
        controls.add(filter, cg);

        JButton moveBtn = new JButton("Move selected bin quantity…");
        WorkspaceShell.styleSecondaryButton(moveBtn);
        moveBtn.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(panel, "Select a bin line in the table first.");
                return;
            }
            int mr = table.convertRowIndexToModel(viewRow);
            String itemCode = Objects.toString(model.getValueAt(mr, 0), "").trim();
            String locationName = Objects.toString(model.getValueAt(mr, 2), "").trim();
            Object qCell = model.getValueAt(mr, 3);
            int maxQty = qCell instanceof Number n ? n.intValue() : Integer.parseInt(Objects.toString(qCell, "0").trim());
            if (itemCode.isBlank() || locationName.isBlank()) {
                JOptionPane.showMessageDialog(panel, "Selected row is missing item code or location.");
                return;
            }
            if (maxQty <= 0) {
                JOptionPane.showMessageDialog(panel, "Nothing available to move from this bin line.");
                return;
            }
            try {
                int fromLocId = WorkspaceShell.resolveStorageLocationIdForItemBin(connection, itemCode, locationName);
                JComboBox<WorkspaceShell.StorageLocationPick> destCombo = new JComboBox<>();
                WorkspaceShell.styleComboMatchInputRow(destCombo);
                WorkspaceShell.fillStorageLocationComboExcluding(destCombo, connection, fromLocId);
                if (destCombo.getItemCount() == 0) {
                    JOptionPane.showMessageDialog(panel, "There is no other bin to move quantity to.");
                    return;
                }
                JTextField qtyField = new JTextField(String.valueOf(maxQty));
                WorkspaceShell.styleInput(qtyField);
                JPanel dialogBody = new JPanel(new GridBagLayout());
                AppUI.applyPanelBackground(dialogBody);
                GridBagConstraints dc = new GridBagConstraints();
                dc.insets = new Insets(4, 0, 4, 10);
                dc.anchor = GridBagConstraints.WEST;
                dc.gridx = 0;
                dc.gridy = 0;
                dc.gridwidth = 2;
                JLabel summary = new JLabel("<html>"
                        + "Item <b>" + WorkspaceShell.htmlEscapePlainTextForJLabel(itemCode) + "</b><br>"
                        + "From <b>" + WorkspaceShell.htmlEscapePlainTextForJLabel(locationName) + "</b> &nbsp;(max " + maxQty + ")"
                        + "</html>");
                dialogBody.add(summary, dc);
                dc.gridwidth = 1;
                dc.gridy = 1;
                dc.fill = GridBagConstraints.HORIZONTAL;
                dc.weightx = 0;
                dialogBody.add(new JLabel("Destination bin *"), dc);
                dc.gridx = 1;
                dc.weightx = 1;
                dialogBody.add(destCombo, dc);
                dc.gridx = 0;
                dc.gridy = 2;
                dc.weightx = 0;
                dialogBody.add(new JLabel("Quantity *"), dc);
                dc.gridx = 1;
                dialogBody.add(qtyField, dc);

                int choice = JOptionPane.showConfirmDialog(
                        panel,
                        dialogBody,
                        "Move bin quantity",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE);
                if (choice != JOptionPane.OK_OPTION) {
                    return;
                }
                Object destSel = destCombo.getSelectedItem();
                if (!(destSel instanceof WorkspaceShell.StorageLocationPick destPick)) {
                    JOptionPane.showMessageDialog(panel, "Choose a destination bin.");
                    return;
                }
                int qtyMove = Integer.parseInt(qtyField.getText().trim());
                if (qtyMove <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                if (qtyMove > maxQty) {
                    JOptionPane.showMessageDialog(panel,
                            "Quantity cannot exceed qty in bin (" + maxQty + ").");
                    return;
                }
                WorkspaceShell.moveInventoryBetweenStorageLocations(connection, user, itemCode, fromLocId, destPick.id, qtyMove);
                JOptionPane.showMessageDialog(panel, "Moved " + qtyMove + " units to " + destPick.label + ".");
                reload.run();
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(panel, "Enter a valid whole number for quantity.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel,
                        "Could not move: " + ex.getMessage(),
                        "Database",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(footer);
        footer.add(controls, BorderLayout.NORTH);
        JPanel moveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(moveRow);
        moveRow.add(moveBtn);
        footer.add(moveRow, BorderLayout.SOUTH);

        panel.add(top, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

        static void refreshStockReportLocationCombo(JComboBox<WorkspaceShell.StorageLocationPick> combo, Connection connection) throws SQLException {
        Object prior = combo.getSelectedItem();
        int priorId = prior instanceof WorkspaceShell.StorageLocationPick sp ? sp.id : WorkspaceShell.STOCK_REPORT_ALL_LOCATIONS_ID;
        combo.removeAllItems();
        combo.addItem(new WorkspaceShell.StorageLocationPick(WorkspaceShell.STOCK_REPORT_ALL_LOCATIONS_ID, "— All locations —"));
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                        SELECT id, name FROM storage_locations
                        ORDER BY sort_order ASC, name COLLATE NOCASE ASC
                        """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    combo.addItem(new WorkspaceShell.StorageLocationPick(rs.getInt("id"), rs.getString("name")));
                }
            }
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            WorkspaceShell.StorageLocationPick cand = combo.getItemAt(i);
            if (cand != null && cand.id == priorId) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.setSelectedIndex(0);
    }

        static void reloadStockByLocationTable(
            DefaultTableModel model,
            Connection connection,
            int locationPickId,
            String filterTrimmed
    ) throws SQLException {
        model.setRowCount(0);
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        StringBuilder sb = new StringBuilder("""
                SELECT s.item_code AS item_code,
                       i.`Item Name` AS item_name,
                       sl.name AS loc_name,
                       s.qty AS bin_qty,
                       i.`Stock` AS total_stock
                FROM inventory_storage_qty s
                JOIN inventory i ON i.`Item Code` = s.item_code
                JOIN storage_locations sl ON sl.id = s.location_id
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (locationPickId != WorkspaceShell.STOCK_REPORT_ALL_LOCATIONS_ID) {
            sb.append(" AND s.location_id = ?");
            params.add(locationPickId);
        }
        if (filterTrimmed != null && !filterTrimmed.isBlank()) {
            sb.append("""
                     AND (
                        LOWER(i.`Item Code`) LIKE '%' || LOWER(?) || '%'
                        OR LOWER(COALESCE(i.`Item Name`, '')) LIKE '%' || LOWER(?) || '%'
                    )
                    """);
            params.add(filterTrimmed);
            params.add(filterTrimmed);
        }
        sb.append("""
                 ORDER BY sl.sort_order ASC,
                          sl.name COLLATE NOCASE ASC,
                          item_code COLLATE NOCASE ASC
                """);
        try (PreparedStatement ps = connection.prepareStatement(sb.toString())) {
            int idx = 1;
            for (Object p : params) {
                if (p instanceof Integer integ) {
                    ps.setInt(idx++, integ);
                } else {
                    ps.setString(idx++, Objects.toString(p, ""));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                            rs.getString("item_code"),
                            rs.getString("item_name"),
                            rs.getString("loc_name"),
                            rs.getInt("bin_qty"),
                            rs.getInt("total_stock")
                    });
                }
            }
        }
    }
}
