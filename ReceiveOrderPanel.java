import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

public final class ReceiveOrderPanel {
    private ReceiveOrderPanel() {}

    /**
     * Builds receive-order panel: receipts post to Stock and FIFO layers; reduces open PO quantities and {@code On Order}.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param workspaceContainer workspace card container
     * @return receive-order panel
     * @throws SQLException when pending-order table fails to load
     */
    public static JPanel build(User user, Connection connection, JPanel workspaceContainer) throws SQLException {
        return build(user, connection, workspaceContainer, null, null);
    }

    /**
     * Builds receive-order panel with optional prefilled reference and item code.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param workspaceContainer workspace card container
     * @param prefillReference optional purchase order reference
     * @param prefillItemCode optional item code
     * @return receive-order panel
     * @throws SQLException when pending-order table fails to load
     */
    public static JPanel build(
            User user,
            Connection connection,
            JPanel workspaceContainer,
            String prefillReference,
            String prefillItemCode
    ) throws SQLException {
        JPanel panel = WorkspaceShell.buildFormPanel("Receive Order");
        JTextField reference = new JTextField();
        JTextField itemCode = new JTextField();
        JTextField recvItemDesc = new JTextField();
        JTextField received = new JTextField();
        JComboBox<WorkspaceShell.StorageLocationPick> storageLocationCombo = new JComboBox<>();
        WorkspaceShell.styleInput(reference, itemCode, recvItemDesc, received);
        WorkspaceShell.styleAutoFilledInventoryField(recvItemDesc);
        WorkspaceShell.refreshActiveStorageLocationCombo(storageLocationCombo, connection);
        WorkspaceShell.styleComboMatchInputRow(storageLocationCombo);
        if (prefillReference != null) {
            reference.setText(prefillReference);
        }
        if (prefillItemCode != null) {
            itemCode.setText(prefillItemCode);
        }

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints rg = new GridBagConstraints();
        rg.insets = new Insets(4, 0, 4, 10);
        int rf = 0;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Reference *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(reference, rg);
        rf++;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Item Code *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(itemCode, rg);
        rf++;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Item Description"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        recvItemDesc.setToolTipText("From inventory row for the entered item code.");
        form.add(recvItemDesc, rg);
        rf++;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Received Quantity *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(received, rg);
        rf++;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Put away to"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        JLabel storageHint = new JLabel("Bins are shelf labels only — total Stock remains the source of truth.");
        storageHint.setFont(storageHint.getFont().deriveFont(11f));
        JPanel storageRow = new JPanel(new BorderLayout(8, 0));
        AppUI.applyPanelBackground(storageRow);
        storageRow.add(storageLocationCombo, BorderLayout.CENTER);
        storageRow.add(storageHint, BorderLayout.SOUTH);
        form.add(storageRow, rg);

        WorkspaceShell.wireInventoryItemDescriptionLookup(connection, itemCode, recvItemDesc);

        DefaultTableModel pendingModel = new DefaultTableModel(PurchaseOrdersPanel.PENDING_ORDER_TABLE_COLUMNS.clone(), 0);
        JTable pendingTable = new JTable(pendingModel);
        WorkspaceShell.installTableCopyMenu(pendingTable);
        TableRowSorter<DefaultTableModel> pendingSorter = new TableRowSorter<>(pendingModel);
        pendingTable.setRowSorter(pendingSorter);
        PurchaseOrdersPanel.loadPendingOrders(pendingModel, connection);
        PurchaseOrdersPanel.hidePendingOrdersRowIdColumn(pendingTable);
        pendingTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int selectedRow = pendingTable.getSelectedRow();
            if (selectedRow < 0) {
                return;
            }
            int modelRow = pendingTable.convertRowIndexToModel(selectedRow);
            itemCode.setText(String.valueOf(pendingModel.getValueAt(modelRow, 1)));
            reference.setText(String.valueOf(pendingModel.getValueAt(modelRow, 7)));
        });
        JScrollPane pendingScroll = new JScrollPane(pendingTable);
        pendingScroll.setBorder(AppUI.newRoundedBorder(8));
        JPanel searchPanel = new JPanel(new BorderLayout(0, 6));
        AppUI.applyPanelBackground(searchPanel);
        searchPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JLabel searchLabel = new JLabel("Search pending orders (code, description, supplier, or reference)");
        searchLabel.setForeground(AppUI.TEXT_MUTED);
        searchLabel.setFont(searchLabel.getFont().deriveFont(Font.PLAIN, 13f));
        JTextField pendingSearch = new JTextField();
        WorkspaceShell.styleInput(pendingSearch);
        searchPanel.add(searchLabel, BorderLayout.NORTH);
        searchPanel.add(pendingSearch, BorderLayout.CENTER);
        pendingSearch.getDocument().addDocumentListener(new DocumentListener() {
            private void applyFilter() {
                String text = pendingSearch.getText();
                if (text == null || text.trim().isEmpty()) {
                    pendingSorter.setRowFilter(null);
                    return;
                }
                String like = text.trim();
                pendingSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(like), 1, 2, 6, 7));
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        Runnable reloadPendingReceive = () -> {
            try {
                PurchaseOrdersPanel.loadPendingOrders(pendingModel, connection);
                WorkspaceShell.deferPackTableColumns(pendingTable);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Unable to refresh pending orders: " + ex.getMessage());
            }
        };

        JButton refreshPending = new JButton("Refresh Pending Orders");
        WorkspaceShell.styleSecondaryButton(refreshPending);
        refreshPending.addActionListener(e -> reloadPendingReceive.run());

        JButton cancelPendingReceive = new JButton("Cancel Pending Line…");
        WorkspaceShell.styleSecondaryButton(cancelPendingReceive);
        cancelPendingReceive.setToolTipText("Clear an open line without receiving; restores On Order. Optional note is logged.");
        cancelPendingReceive.addActionListener(e -> PurchaseOrdersPanel.cancelSelectedPendingOrderLineDialog(
                user, connection, panel, pendingTable, reloadPendingReceive));

        JButton submit = new JButton("Receive");
        AppUI.stylePrimaryButton(submit);
        submit.addActionListener(e -> {
            String ref = reference.getText().trim();
            String code = itemCode.getText().trim();
            if (ref.isEmpty() || code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Reference and item code are required.");
                return;
            }
            try {
                int qty = Integer.parseInt(received.getText().trim());
                if (qty <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                int ordered;
                double purchasePrice;
                try (PreparedStatement ps = connection.prepareStatement("SELECT Amount, `Purchase Price` FROM pendingOrders WHERE `Reference` = ? AND `Item Code` = ?")) {
                    ps.setString(1, ref);
                    ps.setString(2, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            JOptionPane.showMessageDialog(panel, "No pending order line found for that reference and item.");
                            return;
                        }
                        ordered = rs.getInt("Amount");
                        purchasePrice = rs.getDouble("Purchase Price");
                    }
                }
                if (qty > ordered) {
                    JOptionPane.showMessageDialog(panel, "Received quantity cannot exceed ordered amount.");
                    return;
                }
                Object locSelection = storageLocationCombo.getSelectedItem();
                WorkspaceShell.StorageLocationPick locPick = locSelection instanceof WorkspaceShell.StorageLocationPick lp ? lp : null;
                int storageLocationId = locPick != null ? locPick.id : DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID;
                WorkspaceShell.applyReceive(user, connection, ref, code, qty, purchasePrice, storageLocationId);
                reloadPendingReceive.run();
                WorkspaceShell.recordRecentItem(code, WorkspaceShell.queryInventoryItemDescription(connection, code));
                JOptionPane.showMessageDialog(panel, "Items received successfully.");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid quantity.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        AppUI.applyPanelBackground(footer);
        JPanel footerWest = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(footerWest);
        footerWest.add(refreshPending);
        footerWest.add(cancelPendingReceive);
        footer.add(footerWest, BorderLayout.WEST);
        footer.add(submit, BorderLayout.EAST);
        panel.add(form, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(center);
        center.add(pendingScroll, BorderLayout.CENTER);
        center.add(searchPanel, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }
}
