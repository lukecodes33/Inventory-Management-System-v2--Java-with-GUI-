import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

public final class ProcessSalePanel {
    private ProcessSalePanel() {}

    /**
     * Builds process-sale panel with multi-line sale draft table.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param workspaceContainer card layout host panel
     * @return sale processing panel
     * @throws SQLException when storage tables cannot be prepared for bin-aware checkout
     */
    public static JPanel build(User user, Connection connection, JPanel workspaceContainer) throws SQLException {
        DatabaseManager.ensureStorageLocationsAndBuckets(connection);
        boolean trackBins = DatabaseManager.hasInventoryStorageQtyTable(connection);

        JPanel panel = WorkspaceShell.buildFormPanel("Process Sale");

        JLabel hint = WorkspaceShell.buildSectionText(trackBins
                ? "Pick which bin each line ships from (sell-from quantity must be available there). Revenue and profit use sale prices minus FIFO cost."
                : "Enter the unit sale price for each line. Revenue and profit in the top bar use these prices minus FIFO cost.");

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints rg = new GridBagConstraints();
        rg.insets = new Insets(4, 0, 4, 10);
        int rf = 0;
        JTextField itemCode = new JTextField();
        JTextField itemDesc = new JTextField();
        JTextField quantity = new JTextField();
        JTextField unitSalePrice = new JTextField();
        JComboBox<WorkspaceShell.StorageLocationPick> saleLocationCombo = new JComboBox<>();
        WorkspaceShell.styleInput(itemCode, itemDesc, quantity, unitSalePrice);
        WorkspaceShell.styleAutoFilledInventoryField(itemDesc);
        if (trackBins) {
            WorkspaceShell.refreshActiveStorageLocationCombo(saleLocationCombo, connection);
            WorkspaceShell.styleComboMatchInputRow(saleLocationCombo);
        }
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
        itemDesc.setToolTipText("From inventory — updates when Item Code matches a SKU.");
        form.add(itemDesc, rg);
        rf++;
        if (trackBins) {
            rg.gridx = 0;
            rg.gridy = rf;
            rg.anchor = GridBagConstraints.LINE_END;
            rg.fill = GridBagConstraints.NONE;
            rg.weightx = 0;
            form.add(new JLabel("Sell from *"), rg);
            rg.gridx = 1;
            rg.anchor = GridBagConstraints.LINE_START;
            rg.fill = GridBagConstraints.HORIZONTAL;
            rg.weightx = 1;
            JLabel saleLocHint = new JLabel("Bins are shelf labels — total Stock must still cover the sale.");
            saleLocHint.setFont(saleLocHint.getFont().deriveFont(11f));
            JPanel storageRow = new JPanel(new BorderLayout(8, 0));
            AppUI.applyPanelBackground(storageRow);
            storageRow.add(saleLocationCombo, BorderLayout.CENTER);
            storageRow.add(saleLocHint, BorderLayout.SOUTH);
            form.add(storageRow, rg);
            rf++;
        }
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Quantity *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(quantity, rg);
        rf++;
        rg.gridx = 0;
        rg.gridy = rf;
        rg.anchor = GridBagConstraints.LINE_END;
        rg.fill = GridBagConstraints.NONE;
        rg.weightx = 0;
        form.add(new JLabel("Unit sale price *"), rg);
        rg.gridx = 1;
        rg.anchor = GridBagConstraints.LINE_START;
        rg.fill = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1;
        form.add(unitSalePrice, rg);
        rf++;

        WorkspaceShell.wireInventoryItemDescriptionLookup(connection, itemCode, itemDesc);
        wireSaleUnitPriceFromMarket(connection, itemCode, unitSalePrice);

        Map<String, SaleDraftLine> items = new LinkedHashMap<>();
        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Item Code", "Location", "Item Description", "Quantity", "Unit sale price"}, 0);
        JTable table = new JTable(model);
        configureProcessSaleDraftTable(table);

        JButton addLine = new JButton("Add Line");
        WorkspaceShell.styleSecondaryButton(addLine);
        JButton removeLine = new JButton("Remove selected");
        WorkspaceShell.styleSecondaryButton(removeLine);
        JButton clearCart = new JButton("Clear cart");
        WorkspaceShell.styleSecondaryButton(clearCart);
        JButton submit = new JButton("Complete Sale");
        AppUI.stylePrimaryButton(submit);

        JLabel cartSummary = new JLabel(" ");
        cartSummary.setForeground(AppUI.TEXT_MUTED);

        JTextField transactionNote = new JTextField();
        WorkspaceShell.styleInput(transactionNote);
        transactionNote.setToolTipText("Optional — one note for this entire sale (e.g. discount reason).");

        Runnable refreshCartUi = () -> {
            refreshSaleDraftTable(model, items, table);
            int units = 0;
            double total = 0;
            for (SaleDraftLine line : items.values()) {
                units += line.quantity;
                total += line.quantity * line.unitSalePrice;
            }
            cartSummary.setText(items.isEmpty()
                    ? "Cart is empty"
                    : String.format(Locale.US, "%d lines, %d units, %s",
                    items.size(), units, WorkspaceShell.formatUsdMoney(total)));
        };

        addLine.addActionListener(e -> {
            String code = itemCode.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Item code is required.");
                return;
            }
            Integer locId = null;
            String locLabel = "";
            if (trackBins) {
                WorkspaceShell.StorageLocationPick pick = (WorkspaceShell.StorageLocationPick) saleLocationCombo.getSelectedItem();
                if (pick == null) {
                    JOptionPane.showMessageDialog(panel, "Choose a storage location for this line.");
                    return;
                }
                locId = pick.id;
                locLabel = pick.label;
            }
            String key = saleDraftLineMapKey(code, locId);
            try {
                int qty = Integer.parseInt(quantity.getText().trim());
                if (qty <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                double unitPrice = Double.parseDouble(unitSalePrice.getText().trim());
                if (unitPrice < 0) {
                    JOptionPane.showMessageDialog(panel, "Unit sale price cannot be negative.");
                    return;
                }
                int stock = WorkspaceShell.getIntValue(connection, "SELECT `Stock` FROM inventory WHERE `Item Code` = ?", code);
                if (stock <= 0) {
                    JOptionPane.showMessageDialog(panel, "Item is out of stock or not found.");
                    return;
                }
                int sumSameSku = 0;
                for (SaleDraftLine dl : items.values()) {
                    if (dl.itemCode.equals(code)) {
                        sumSameSku += dl.quantity;
                    }
                }
                int projectedSkuTotal = sumSameSku + qty;
                if (items.containsKey(key)) {
                    projectedSkuTotal -= items.get(key).quantity;
                }
                if (projectedSkuTotal > stock) {
                    JOptionPane.showMessageDialog(panel, "Not enough stock available for this SKU across this sale (including other bins).");
                    return;
                }
                if (trackBins && locId != null) {
                    int binQty = WorkspaceShell.getInventoryStorageQtyAtLocation(connection, code, locId);
                    int alreadyAtKey = items.containsKey(key) ? items.get(key).quantity : 0;
                    if (alreadyAtKey + qty > binQty) {
                        JOptionPane.showMessageDialog(panel,
                                "Not enough quantity in the selected bin for this line (including lines already in the sale).");
                        return;
                    }
                }
                String nameForLine = WorkspaceShell.queryInventoryItemDescription(connection, code);
                if (items.containsKey(key)) {
                    SaleDraftLine existing = items.get(key);
                    if (Double.compare(existing.unitSalePrice, unitPrice) != 0) {
                        JOptionPane.showMessageDialog(panel,
                                "This item from this bin is already on the sale at a different unit price. Remove it first or use the same price.");
                        return;
                    }
                    existing.quantity += qty;
                } else {
                    items.put(key, new SaleDraftLine(code, qty, unitPrice, nameForLine, locId, locLabel));
                }
                refreshCartUi.run();
                itemCode.setText("");
                quantity.setText("");
                unitSalePrice.setText("");
                itemDesc.setText("");
                itemCode.requestFocusInWindow();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid quantity and unit sale price.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });

        removeLine.addActionListener(e -> {
            int vr = table.getSelectedRow();
            if (vr < 0) {
                JOptionPane.showMessageDialog(panel, "Select a cart line to remove.");
                return;
            }
            int mr = table.convertRowIndexToModel(vr);
            String code = Objects.toString(model.getValueAt(mr, 0), "").trim();
            String locLabel = Objects.toString(model.getValueAt(mr, 1), "").trim();
            String keyToRemove = null;
            for (Map.Entry<String, SaleDraftLine> entry : items.entrySet()) {
                SaleDraftLine line = entry.getValue();
                String label = line.storageLocationLabel.isEmpty() ? "—" : line.storageLocationLabel;
                if (line.itemCode.equals(code) && label.equals(locLabel)) {
                    keyToRemove = entry.getKey();
                    break;
                }
            }
            if (keyToRemove != null) {
                items.remove(keyToRemove);
                refreshCartUi.run();
            }
        });

        clearCart.addActionListener(e -> {
            if (items.isEmpty()) {
                return;
            }
            int ok = JOptionPane.showConfirmDialog(panel, "Remove all lines from the cart?", "Clear cart",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok == JOptionPane.OK_OPTION) {
                items.clear();
                refreshCartUi.run();
                itemCode.requestFocusInWindow();
            }
        });

        submit.addActionListener(e -> {
            if (items.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Add at least one item first.");
                return;
            }
            String rawNote = transactionNote.getText();
            if (rawNote != null && rawNote.length() > WorkspaceShell.MAX_SALE_TRANSACTION_NOTE_LENGTH) {
                JOptionPane.showMessageDialog(panel,
                        "Transaction note must be at most " + WorkspaceShell.MAX_SALE_TRANSACTION_NOTE_LENGTH + " characters.");
                return;
            }
            String reference = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String now = dateTime.nowDisplayString();
            try {
                processSaleTransaction(connection, user, items, reference, now, rawNote);
                for (SaleDraftLine line : items.values()) {
                    WorkspaceShell.recordRecentItem(line.itemCode, line.itemDescription);
                }
                JOptionPane.showMessageDialog(panel, "Sale completed. Reference: " + reference);
                transactionNote.setText("");
                WorkspaceShell.showView(workspaceContainer, "View Sales Transaction", SalesPanel.build(user, connection, workspaceContainer));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });

        JPanel upper = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(upper);
        upper.add(hint, BorderLayout.NORTH);
        upper.add(form, BorderLayout.CENTER);
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(addRow);
        addRow.add(addLine);
        upper.add(addRow, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(body);
        body.add(upper, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(AppUI.newRoundedBorder(8));
        body.add(tableScroll, BorderLayout.CENTER);
        JPanel cartActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(cartActions);
        cartActions.add(removeLine);
        cartActions.add(clearCart);
        cartActions.add(cartSummary);
        body.add(cartActions, BorderLayout.SOUTH);

        refreshCartUi.run();

        JPanel footer = new JPanel(new GridBagLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        AppUI.applyPanelBackground(footer);
        GridBagConstraints gf = new GridBagConstraints();
        gf.insets = new Insets(4, 0, 4, 10);
        gf.gridx = 0;
        gf.gridy = 0;
        gf.anchor = GridBagConstraints.LINE_END;
        gf.fill = GridBagConstraints.NONE;
        gf.weightx = 0;
        footer.add(new JLabel("Note (optional)"), gf);
        gf.gridx = 1;
        gf.anchor = GridBagConstraints.LINE_START;
        gf.fill = GridBagConstraints.HORIZONTAL;
        gf.weightx = 1;
        footer.add(transactionNote, gf);
        gf.gridx = 2;
        gf.anchor = GridBagConstraints.LINE_END;
        gf.fill = GridBagConstraints.NONE;
        gf.weightx = 0;
        footer.add(submit, gf);

        panel.add(body, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(() -> itemCode.requestFocusInWindow());
        return panel;
    }

    /**
     * Executes a sale transaction atomically across all related tables.
     *
     * @param connection active database connection
     * @param user active signed-in user
     * @param items map keyed by {@link #saleDraftLineMapKey}; values hold SKU, bin (when tracked), quantity and unit sale price
     * @param reference sale reference
     * @param now formatted timestamp
     * @param transactionNote optional single note for the whole checkout (stored on each line row)
     * @throws SQLException when transaction fails
     */
    static void processSaleTransaction(
            Connection connection,
            User user,
            Map<String, SaleDraftLine> items,
            String reference,
            String now,
            String transactionNote
    ) throws SQLException {
        String noteForDb = WorkspaceShell.normalizedSaleTransactionNote(transactionNote);
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (Map.Entry<String, SaleDraftLine> entry : items.entrySet()) {
                SaleDraftLine line = entry.getValue();
                String code = line.itemCode;
                int qty = line.quantity;
                double unitSalePrice = line.unitSalePrice;
                String itemName;
                try (PreparedStatement fetch = connection.prepareStatement("SELECT `Item Name` FROM inventory WHERE `Item Code` = ?")) {
                    fetch.setString(1, code);
                    try (ResultSet rs = fetch.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Item not found: " + code);
                        }
                        itemName = rs.getString("Item Name");
                    }
                }

                double fifoCost = InventoryFifo.fifoCostWithLatestLayerFallback(connection, code, qty);

                String dateIso = WorkspaceShell.toIsoDateTime(now);
                try (PreparedStatement insertSale = connection.prepareStatement(
                        "INSERT INTO sales (`Item Code`, `Item Name`, `Amount`, `Total Price`, `Total Cost`, `Reference`, `User`, `Date`, `DateISO`, `Note`, storage_location_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )) {
                    insertSale.setString(1, code);
                    insertSale.setString(2, itemName);
                    insertSale.setInt(3, qty);
                    insertSale.setDouble(4, unitSalePrice * qty);
                    insertSale.setDouble(5, fifoCost);
                    insertSale.setString(6, reference);
                    insertSale.setString(7, user.getUsername());
                    insertSale.setString(8, now);
                    insertSale.setString(9, dateIso);
                    insertSale.setString(10, noteForDb);
                    if (line.storageLocationId == null) {
                        insertSale.setNull(11, java.sql.Types.INTEGER);
                    } else {
                        insertSale.setInt(11, line.storageLocationId);
                    }
                    insertSale.executeUpdate();
                }
                try (PreparedStatement updateStock = connection.prepareStatement("UPDATE inventory SET `Stock` = `Stock` - ? WHERE `Item Code` = ? AND `Stock` >= ?")) {
                    updateStock.setInt(1, qty);
                    updateStock.setString(2, code);
                    updateStock.setInt(3, qty);
                    int affected = updateStock.executeUpdate();
                    if (affected == 0) {
                        throw new SQLException("Insufficient stock while processing sale for item: " + code);
                    }
                }
                if (line.storageLocationId != null) {
                    WorkspaceShell.deductInventoryStorageQtyAtLocation(connection, code, line.storageLocationId, qty);
                } else {
                    WorkspaceShell.deductInventoryStorageQtySpread(connection, code, qty);
                }
                String saleReason = "CUSTOMER_SALE";
                if (line.storageLocationId != null) {
                    saleReason += " locId=" + line.storageLocationId;
                }
                try (PreparedStatement movement = connection.prepareStatement("INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    movement.setString(1, code);
                    movement.setString(2, String.valueOf(qty));
                    movement.setString(3, "SALE");
                    movement.setString(4, saleReason);
                    movement.setString(5, user.getUsername());
                    movement.setString(6, now);
                    movement.executeUpdate();
                }
            }

            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    /** One in-progress sale line (quantity and unit price) before checkout. */
    static final class SaleDraftLine {
        final String itemCode;
        int quantity;
        double unitSalePrice;
    /** {@code Inventory.Item Name} captured when the line is added (for the draft grid). */
        String itemDescription;
    /**
         * Bin for this line when {@link DatabaseManager#hasInventoryStorageQtyTable(Connection)} is true;
         * otherwise {@code null} (allocation uses legacy spread across bins).
         */
        final Integer storageLocationId;
    /** Display label for {@link #storageLocationId} (draft table). */
        String storageLocationLabel;

        SaleDraftLine(
                String itemCode,
                int quantity,
                double unitSalePrice,
                String itemDescription,
                Integer storageLocationId,
                String storageLocationLabel
        ) {
            this.itemCode = itemCode == null ? "" : itemCode;
            this.quantity = quantity;
            this.unitSalePrice = unitSalePrice;
            this.itemDescription = itemDescription == null ? "" : itemDescription;
            this.storageLocationId = storageLocationId;
            this.storageLocationLabel = storageLocationLabel == null ? "" : storageLocationLabel;
        }
    }

    /** Map key for sale draft lines (same SKU may appear from different bins). */
    static String saleDraftLineMapKey(String itemCode, Integer storageLocationId) {
        return itemCode + '\u0001' + (storageLocationId == null ? "-" : storageLocationId.toString());
    }

    /** Rebuilds draft line table rows from map-backed values. */
    static void refreshSaleDraftTable(DefaultTableModel model, Map<String, SaleDraftLine> map, JTable table) {
        model.setRowCount(0);
        for (SaleDraftLine line : map.values()) {
            String locCell = line.storageLocationLabel.isEmpty() ? "—" : line.storageLocationLabel;
            model.addRow(new Object[]{line.itemCode, locCell, line.itemDescription, line.quantity, line.unitSalePrice});
        }
        WorkspaceShell.deferPackTableColumns(table);
    }

    /** Draft table on Process Sale: no cell right-click menu; no vertical grid lines (cleaner columns). */
    static void configureProcessSaleDraftTable(JTable table) {
        table.setRowHeight(WorkspaceShell.TABLE_ROW_HEIGHT);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        if (table.getTableHeader() != null) {
            table.getTableHeader().setReorderingAllowed(false);
            table.getTableHeader().setResizingAllowed(true);
        }
        WorkspaceShell.deferPackTableColumns(table);
    }

    /**
     * When unit sale price is still blank, pre-fills from market price as the item code is typed.
     */
    static void wireSaleUnitPriceFromMarket(
            Connection connection,
            JTextField itemCodeField,
            JTextField unitSalePriceField
    ) {
        DocumentListener dl = new DocumentListener() {
            private void apply() {
                SwingUtilities.invokeLater(() -> {
                    String current = unitSalePriceField.getText();
                    if (current != null && !current.trim().isEmpty()) {
                        return;
                    }
                    String code = itemCodeField.getText().trim();
                    if (code.isEmpty()) {
                        return;
                    }
                    Double mp = WorkspaceShell.queryInventoryMarketPrice(connection, code);
                    if (mp != null) {
                        unitSalePriceField.setText(String.format(Locale.US, "%.2f", mp));
                    }
                });
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                apply();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                apply();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                apply();
            }
        };
        itemCodeField.getDocument().addDocumentListener(dl);
    }
}
