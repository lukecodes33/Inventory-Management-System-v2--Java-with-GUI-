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
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

public final class PurchaseOrdersPanel {
    private PurchaseOrdersPanel() {}

    /**
     * Builds purchase order create-and-review panel.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param workspaceContainer workspace card container
     * @return purchase-order panel
     * @throws SQLException when pending data fails to load
     */
    public static JPanel build(User user, Connection connection, JPanel workspaceContainer) throws SQLException {
        JPanel panel = WorkspaceShell.buildFormPanel(WorkspaceShell.VIEW_PO_TRACKING);

        JTextField referenceField = new JTextField();
        JTextField itemCodeField = new JTextField();
        JTextField itemDescriptionField = new JTextField();
        JTextField quantityField = new JTextField();
        JTextField purchasePriceField = new JTextField();
        JTextField remainingPaymentField = new JTextField();
        JTextField purchasedFromField = new JTextField();
        WorkspaceShell.styleInput(referenceField, itemCodeField, itemDescriptionField, quantityField, purchasePriceField, remainingPaymentField, purchasedFromField);
        WorkspaceShell.styleAutoFilledInventoryField(itemDescriptionField);

        JPanel refReuseRow = new JPanel(new BorderLayout(8, 0));
        AppUI.applyPanelBackground(refReuseRow);
        refReuseRow.add(referenceField, BorderLayout.CENTER);
        JCheckBox reusePoReference = new JCheckBox(
                "Reuse after create",
                false
        );
        reusePoReference.setToolTipText("Keep this PO / tracking number after Create (multiple lines same reference)");
        AppUI.applyPanelBackground(reusePoReference);
        refReuseRow.add(reusePoReference, BorderLayout.EAST);

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints gb = new GridBagConstraints();
        gb.insets = new Insets(4, 0, 4, 10);
        int r = 0;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("PO / Tracking Number (optional)"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(refReuseRow, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Item Code *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(itemCodeField, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Item Description"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        itemDescriptionField.setToolTipText("From inventory — updates when Item Code matches a SKU.");
        form.add(itemDescriptionField, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Quantity *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(quantityField, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Purchase Price *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(purchasePriceField, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Remaining Payment"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        remainingPaymentField.setToolTipText("Outstanding amount still owed for this PO line (optional; defaults to 0).");
        form.add(remainingPaymentField, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Purchased From *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        purchasedFromField.setToolTipText("Vendor or seller this line was purchased from (required).");
        form.add(purchasedFromField, gb);

        WorkspaceShell.wireInventoryItemDescriptionLookup(connection, itemCodeField, itemDescriptionField);

        DefaultTableModel pendingModel = new DefaultTableModel(PENDING_ORDER_TABLE_COLUMNS.clone(), 0);
        JTable pendingTable = new JTable(pendingModel);
        WorkspaceShell.installTableCopyMenu(pendingTable);
        hidePendingOrdersRowIdColumn(pendingTable);
        loadPendingOrders(pendingModel, connection);

        JButton submit = new JButton("Create Purchase Order");
        AppUI.stylePrimaryButton(submit);
        submit.setPreferredSize(new Dimension(240, 36));

        Runnable clearLineFieldsOnly = () -> {
            itemCodeField.setText("");
            quantityField.setText("");
            purchasePriceField.setText("");
            remainingPaymentField.setText("");
            purchasedFromField.setText("");
        };

        Runnable reloadPendingEmbedded = () -> {
            try {
                loadPendingOrders(pendingModel, connection);
                WorkspaceShell.deferPackTableColumns(pendingTable);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel,
                        "Could not reload pending orders: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        submit.addActionListener(e -> {
            try {
                String enteredCode = itemCodeField.getText().trim();
                String enteredQtyText = quantityField.getText().trim();
                String enteredPurchasePriceText = purchasePriceField.getText().trim();
                String remainingPaymentRaw = remainingPaymentField.getText().trim();
                String purchasedFromRaw = purchasedFromField.getText().trim();
                if (enteredCode.isEmpty() || enteredQtyText.isEmpty() || enteredPurchasePriceText.isEmpty()
                        || purchasedFromRaw.isEmpty()) {
                    JOptionPane.showMessageDialog(panel, "Item code, quantity, purchase price, and purchased from are required.");
                    return;
                }
                int enteredQty = Integer.parseInt(enteredQtyText);
                double enteredPurchasePrice = Double.parseDouble(enteredPurchasePriceText);
                double remainingPayment = remainingPaymentRaw.isEmpty() ? 0 : Double.parseDouble(remainingPaymentRaw);
                if (enteredQty <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                if (enteredPurchasePrice < 0) {
                    JOptionPane.showMessageDialog(panel, "Purchase price must be zero or greater.");
                    return;
                }
                if (remainingPayment < 0) {
                    JOptionPane.showMessageDialog(panel, "Remaining payment must be zero or greater.");
                    return;
                }
                try (PreparedStatement exists = connection.prepareStatement("SELECT COUNT(*) AS count FROM inventory WHERE `Item Code` = ?")) {
                    exists.setString(1, enteredCode);
                    try (ResultSet rs = exists.executeQuery()) {
                        if (rs.next() && rs.getInt("count") == 0) {
                            JOptionPane.showMessageDialog(panel, "That item code was not found.");
                            return;
                        }
                    }
                }

                String reference = referenceField.getText().trim();
                if (reference.isEmpty()) {
                    reference = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                }
                String now = dateTime.nowDisplayString();

                int supplierKey = DatabaseManager.ensureSupplier(connection, purchasedFromRaw);

                try (PreparedStatement insertPending = connection.prepareStatement(
                        "INSERT INTO pendingOrders (`Item Code`, `Amount`, `Purchase Price`, `Remaining Payment`, `Purchased From`, `Reference`, `User`, `Date`, supplier_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                ) {
                    insertPending.setString(1, enteredCode);
                    insertPending.setInt(2, enteredQty);
                    insertPending.setDouble(3, enteredPurchasePrice);
                    insertPending.setDouble(4, remainingPayment);
                    insertPending.setString(5, purchasedFromRaw);
                    insertPending.setString(6, reference);
                    insertPending.setString(7, user.getUsername());
                    insertPending.setString(8, now);
                    insertPending.setInt(9, supplierKey);
                    insertPending.executeUpdate();
                }
                try (PreparedStatement updateInventory = connection.prepareStatement("UPDATE inventory SET `On Order` = `On Order` + ? WHERE `Item Code` = ?")) {
                    updateInventory.setInt(1, enteredQty);
                    updateInventory.setString(2, enteredCode);
                    updateInventory.executeUpdate();
                }
                try (PreparedStatement movement = connection.prepareStatement("INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    movement.setString(1, enteredCode);
                    movement.setString(2, String.valueOf(enteredQty));
                    movement.setString(3, "ORDERED");
                    movement.setString(4, "PURCHASE_ORDER_CREATED");
                    movement.setString(5, user.getUsername());
                    movement.setString(6, now);
                    movement.executeUpdate();
                }

                JOptionPane.showMessageDialog(panel, "Purchase order created. Reference: " + reference);
                if (reusePoReference.isSelected()) {
                    referenceField.setText(reference);
                    clearLineFieldsOnly.run();
                } else {
                    referenceField.setText("");
                    clearLineFieldsOnly.run();
                }
                reloadPendingEmbedded.run();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter valid numeric values for quantity, purchase price, and remaining payment.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });

        JScrollPane pendingScroll = new JScrollPane(pendingTable);
        pendingScroll.setBorder(AppUI.newRoundedBorder(8));

        JButton receiveThisLine = new JButton("Receive this line");
        WorkspaceShell.styleSecondaryButton(receiveThisLine);
        receiveThisLine.addActionListener(e -> {
            int vr = pendingTable.getSelectedRow();
            if (vr < 0) {
                JOptionPane.showMessageDialog(panel, "Select a pending line first.");
                return;
            }
            int mr = pendingTable.convertRowIndexToModel(vr);
            String code = Objects.toString(pendingModel.getValueAt(mr, 1), "").trim();
            String ref = Objects.toString(pendingModel.getValueAt(mr, 7), "").trim();
            if (code.isEmpty() || ref.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Could not resolve item code/reference from selected line.");
                return;
            }
            try {
                WorkspaceShell.showView(workspaceContainer, "Receive Order",
                        ReceiveOrderPanel.build(user, connection, workspaceContainer, ref, code));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Unable to open Receive Order: " + ex.getMessage(),
                        "View Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton receiveEntirePo = new JButton("Receive entire PO");
        AppUI.stylePrimaryButton(receiveEntirePo);
        receiveEntirePo.setToolTipText("Receive all remaining lines for the selected PO reference into one bin.");
        receiveEntirePo.addActionListener(e -> {
            String ref = "";
            int vr = pendingTable.getSelectedRow();
            if (vr >= 0) {
                int mr = pendingTable.convertRowIndexToModel(vr);
                ref = Objects.toString(pendingModel.getValueAt(mr, 7), "").trim();
            }
            if (ref.isEmpty()) {
                ref = JOptionPane.showInputDialog(panel, "Enter PO reference to receive in full:", "Receive entire PO",
                        JOptionPane.QUESTION_MESSAGE);
                if (ref == null) {
                    return;
                }
                ref = ref.trim();
            }
            if (ref.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "A PO reference is required.");
                return;
            }
            try {
                showReceiveEntirePoDialog(panel, user, connection, ref, reloadPendingEmbedded);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Could not load PO lines: " + ex.getMessage(),
                        "Receive entire PO", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel centerBlock = new JPanel(new BorderLayout(8, 8));
        AppUI.applyPanelBackground(centerBlock);
        centerBlock.add(form, BorderLayout.NORTH);
        centerBlock.add(pendingScroll, BorderLayout.CENTER);
        JPanel pendingFooter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        AppUI.applyPanelBackground(pendingFooter);
        pendingFooter.add(receiveThisLine);
        pendingFooter.add(receiveEntirePo);
        centerBlock.add(pendingFooter, BorderLayout.SOUTH);

        panel.add(centerBlock, BorderLayout.CENTER);
        panel.add(WorkspaceShell.buildActionBar(null, submit), BorderLayout.SOUTH);
        return panel;
    }

    /** Table model column titles for pending lines; column 0 is SQLite {@code rowid} (shown with zero-width header). */
    static final String[] PENDING_ORDER_TABLE_COLUMNS = new String[]{
            "\u200B", "Item Code", "Item Description", "Amount", "Purchase Price", "Remaining Payment", "Purchased From", "Reference", "Date"
    };

    /** Narrow/hide SQLite rowid column (index 0) on embedded pending-order tables. */
    static void hidePendingOrdersRowIdColumn(JTable pendingTable) {
        TableColumn col = pendingTable.getColumnModel().getColumn(0);
        col.setMinWidth(0);
        col.setMaxWidth(0);
        col.setPreferredWidth(0);
        col.setResizable(false);
    }

    /** Loads pending order lines into the supplied table model ({@link #PENDING_ORDER_TABLE_COLUMNS} including SQLite rowid). */
    static void loadPendingOrders(DefaultTableModel model, Connection connection) throws SQLException {
        model.setRowCount(0);
        String sql = """
                SELECT po.rowid,
                       po.`Item Code`,
                       COALESCE(inv.`Item Name`, '') AS `Item Description`,
                       po.`Amount`,
                       po.`Purchase Price`,
                       po.`Remaining Payment`,
                       po.`Purchased From`,
                       po.`Reference`,
                       po.`Date`
                FROM pendingOrders po
                LEFT JOIN inventory inv ON inv.`Item Code` = po.`Item Code`
                ORDER BY po.`Reference` ASC, po.`Item Code` ASC
                """;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getLong(1),
                        rs.getString("Item Code"),
                        rs.getString("Item Description"),
                        rs.getInt("Amount"),
                        rs.getDouble("Purchase Price"),
                        rs.getDouble("Remaining Payment"),
                        rs.getString("Purchased From"),
                        rs.getString("Reference"),
                        rs.getString("Date")
                });
            }
        }
    }

    /**
     * Removes one open pending-order line by {@code pendingOrders.rowid}, restores {@code On Order}, writes a cancellation movement.
     *
     * @param reason nullable or blank; shortened to {@link #WorkspaceShell.PO_CANCEL_REASON_MAX_CHARS}
     */
    static void applyPendingOrderCancellation(User user, Connection connection, long rowId, String reason)
            throws SQLException {
        final String trimmed = reason == null ? "" : reason.trim();
        final String detail = trimmed.length() > WorkspaceShell.PO_CANCEL_REASON_MAX_CHARS
                ? trimmed.substring(0, WorkspaceShell.PO_CANCEL_REASON_MAX_CHARS)
                : trimmed;

        boolean savedAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            String itemCode;
            int qtyOpen;
            String reference;
            try (PreparedStatement sel = connection.prepareStatement(
                    "SELECT `Item Code`, `Amount`, `Reference` FROM pendingOrders WHERE rowid = ?")) {
                sel.setLong(1, rowId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("That pending line was not found (try refreshing the table).");
                    }
                    itemCode = rs.getString("Item Code");
                    qtyOpen = rs.getInt("Amount");
                    reference = rs.getString("Reference");
                }
            }

            try (PreparedStatement del = connection.prepareStatement("DELETE FROM pendingOrders WHERE rowid = ?")) {
                del.setLong(1, rowId);
                if (del.executeUpdate() != 1) {
                    throw new SQLException("Could not delete the pending order line.");
                }
            }

            try (PreparedStatement decrease = connection.prepareStatement(
                    "UPDATE inventory SET `On Order` = `On Order` - ? WHERE `Item Code` = ? AND `On Order` >= ?")) {
                decrease.setInt(1, qtyOpen);
                decrease.setString(2, itemCode);
                decrease.setInt(3, qtyOpen);
                if (decrease.executeUpdate() != 1) {
                    throw new SQLException(
                            "`On Order` mismatch for item " + itemCode + "; cancel aborted to preserve inventory totals.");
                }
            }

            StringBuilder reasonTxt = new StringBuilder("PO_CANCEL ref=");
            reasonTxt.append(reference).append(" qty=").append(qtyOpen).append(" item=").append(itemCode);
            if (!detail.isEmpty()) {
                String oneLine = detail.replace('\r', ' ').replace('\n', ' ');
                reasonTxt.append(" | ").append(oneLine);
            }
            try (PreparedStatement movement = connection.prepareStatement(
                    "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                movement.setString(1, itemCode);
                movement.setString(2, String.valueOf(qtyOpen));
                movement.setString(3, "ORDER_CANCELLED");
                movement.setString(4, reasonTxt.toString());
                movement.setString(5, user.getUsername());
                movement.setString(6, dateTime.nowDisplayString());
                movement.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // ignore rollback failure
            }
            throw e;
        } finally {
            connection.setAutoCommit(savedAutoCommit);
        }
    }

    /** Confirms cancellation, prompts for optional reason, runs {@link #applyPendingOrderCancellation}, reloads pending table. */
    static void cancelSelectedPendingOrderLineDialog(
            User user,
            Connection connection,
            Component dialogParent,
            JTable pendingTable,
            Runnable reloadPendingSafe
    ) {
        int viewRow = pendingTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(dialogParent, "Select an open PO line in the table first.", "Cancel PO Line",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DefaultTableModel m = (DefaultTableModel) pendingTable.getModel();
        int mr = pendingTable.convertRowIndexToModel(viewRow);
        Object ridObj = m.getValueAt(mr, 0);
        if (!(ridObj instanceof Number ridNum)) {
            JOptionPane.showMessageDialog(dialogParent, "Unable to resolve that row.", "Cancel PO Line",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        long rowId = ridNum.longValue();
        String code = Objects.toString(m.getValueAt(mr, 1), "").trim();
        String ref = Objects.toString(m.getValueAt(mr, 7), "").trim();
        Object amtObj = m.getValueAt(mr, 3);
        String amtStr = amtObj instanceof Number n ? Integer.toString(n.intValue()) : Objects.toString(amtObj, "?");

        int confirm = JOptionPane.showConfirmDialog(dialogParent,
                "Cancel open line " + amtStr + " × " + code + " — reference \"" + ref + "\"?\n"
                        + "`On Order` will decrease by this amount.",
                "Cancel PO / tracking line?",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        String reason = JOptionPane.showInputDialog(dialogParent,
                "Optional reason (e.g. lost shipment, cancelled by seller):",
                "Cancel PO line",
                JOptionPane.PLAIN_MESSAGE);
        if (reason == null) {
            return;
        }

        try {
            applyPendingOrderCancellation(user, connection, rowId, reason);
            JOptionPane.showMessageDialog(dialogParent, "Pending order line cancelled.", "Cancelled",
                    JOptionPane.INFORMATION_MESSAGE);
            reloadPendingSafe.run();
            WorkspaceShell.requestMetricsRefresh();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(dialogParent, ex.getMessage(), "Cancel failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

        static void showReceiveEntirePoDialog(
            Component parent,
            User user,
            Connection connection,
            String reference,
            Runnable onSuccess
    ) throws SQLException {
        List<WorkspaceShell.PendingReceiveLine> lines = WorkspaceShell.loadPendingReceiveLinesForReference(connection, reference);
        if (lines.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "No open lines remain for reference \"" + reference + "\".",
                    "Receive entire PO", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        DefaultTableModel previewModel = new DefaultTableModel(
                new String[]{"Item Code", "Item Name", "Qty to receive", "Unit cost"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        int totalUnits = 0;
        for (WorkspaceShell.PendingReceiveLine line : lines) {
            previewModel.addRow(new Object[]{
                    line.itemCode(),
                    line.itemName(),
                    line.amount(),
                    WorkspaceShell.formatUsdMoney(line.purchasePrice())
            });
            totalUnits += line.amount();
        }
        JTable previewTable = new JTable(previewModel);
        WorkspaceShell.installTableCopyMenu(previewTable);
        JScrollPane previewScroll = new JScrollPane(previewTable);
        previewScroll.setPreferredSize(new Dimension(520, Math.min(220, 28 + lines.size() * 22)));

        JComboBox<WorkspaceShell.StorageLocationPick> binCombo = new JComboBox<>();
        WorkspaceShell.refreshActiveStorageLocationCombo(binCombo, connection);
        WorkspaceShell.styleComboMatchInputRow(binCombo);

        JPanel dialogBody = new JPanel(new BorderLayout(0, 10));
        AppUI.applyPanelBackground(dialogBody);
        dialogBody.add(WorkspaceShell.buildSectionText(
                "Receive all " + lines.size() + " open line(s) (" + totalUnits + " units) for reference "
                        + reference + "."), BorderLayout.NORTH);
        dialogBody.add(previewScroll, BorderLayout.CENTER);
        JPanel binRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(binRow);
        binRow.add(new JLabel("Receive into bin"));
        binRow.add(binCombo);
        dialogBody.add(binRow, BorderLayout.SOUTH);

        int choice = JOptionPane.showConfirmDialog(
                parent,
                dialogBody,
                "Receive entire PO",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        Object locSelection = binCombo.getSelectedItem();
        WorkspaceShell.StorageLocationPick locPick = locSelection instanceof WorkspaceShell.StorageLocationPick lp ? lp : null;
        int storageLocationId = locPick != null ? locPick.id : DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID;
        try {
            WorkspaceShell.receiveEntirePurchaseOrder(user, connection, reference, storageLocationId, lines);
            for (WorkspaceShell.PendingReceiveLine line : lines) {
                WorkspaceShell.recordRecentItem(line.itemCode(), line.itemName());
            }
            WorkspaceShell.requestMetricsRefresh();
            JOptionPane.showMessageDialog(parent,
                    "Received " + lines.size() + " line(s), " + totalUnits + " units for " + reference + ".",
                    "Receive entire PO", JOptionPane.INFORMATION_MESSAGE);
            if (onSuccess != null) {
                onSuccess.run();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Database error: " + ex.getMessage(),
                    "Receive entire PO", JOptionPane.ERROR_MESSAGE);
        }
    }
}
